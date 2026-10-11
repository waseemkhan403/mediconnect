package com.mediconnect.security;

import com.mediconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Authentication for the WebSocket. A browser cannot put headers on the WebSocket handshake, so /ws itself is
 * open and the access token travels in the STOMP CONNECT frame instead:
 *  - CONNECT: the token is validated (signature + expiry, same decoder as the REST API) and the login must still
 *    be active. The connection's user name becomes the user id, which is what /user/... destinations route on.
 *  - SUBSCRIBE: only to the caller's own /user/queue/... destinations, so nobody can listen to another queue.
 *  - SEND: refused. Messages are sent with POST /chats/{id}/messages, where all the chat rules live.
 * Any failure answers with a STOMP ERROR frame and closes the connection.
 */
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
        } else if (command == StompCommand.SUBSCRIBE) {
            String destination = accessor.getDestination();
            if (accessor.getUser() == null || destination == null || !destination.startsWith("/user/queue/")) {
                throw new MessagingException("Subscribe only to /user/queue/chat");
            }
        } else if (command == StompCommand.SEND) {
            throw new MessagingException("Send messages with POST /chats/{id}/messages");
        }
        return message;
    }

    private JwtAuthenticationToken authenticate(String header) {
        if (header == null || !header.startsWith(BEARER)) {
            throw new MessagingException("Missing access token");
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(header.substring(BEARER.length()));
        } catch (JwtException ex) {
            throw new MessagingException("Invalid or expired access token");
        }
        boolean active = userRepository.findById(Long.valueOf(jwt.getSubject()))
                .map(u -> u.isActive())
                .orElse(false);
        if (!active) {
            throw new MessagingException("Account is inactive");
        }
        // getName() of this token is the subject, i.e. the user id.
        return new JwtAuthenticationToken(jwt, List.of());
    }
}
