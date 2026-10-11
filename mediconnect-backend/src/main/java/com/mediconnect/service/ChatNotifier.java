package com.mediconnect.service;

import com.mediconnect.dto.chat.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;

/**
 * Pushes a new chat message to both parties over WebSocket, to the private destination /user/queue/chat of each
 * login (every open tab or device of that login receives it, the sender's included).
 * Runs only AFTER the transaction commits: a message is never pushed if saving it rolled back.
 * Delivery is best effort: someone offline simply loads the messages over REST when they open the chat.
 */
@Component
@RequiredArgsConstructor
public class ChatNotifier {

    public static final String USER_DESTINATION = "/queue/chat";

    /** Published by ChatService after saving a message. */
    public record MessageSent(Set<Long> recipientUserIds, ChatMessageResponse message) {
    }

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(MessageSent event) {
        for (Long userId : event.recipientUserIds()) {
            // The WebSocket principal's name is the user id (see StompAuthInterceptor).
            messagingTemplate.convertAndSendToUser(String.valueOf(userId), USER_DESTINATION, event.message());
        }
    }
}
