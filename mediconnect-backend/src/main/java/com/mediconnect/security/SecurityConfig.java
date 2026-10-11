package com.mediconnect.security;

import com.mediconnect.config.MessageUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Stateless API secured with bearer JWTs.
 *  - /public/** and the sign-in endpoints are open; everything else needs a valid access token.
 *  - Per-endpoint roles are declared with @PreAuthorize on the controllers (see Access).
 *  - CSRF protection is off: access tokens travel in the Authorization header, which a browser never attaches
 *    automatically. The only cookie (refresh token) is SameSite=Strict, scoped to /auth, and its endpoints
 *    return the new token in the body, which a cross-site page cannot read.
 *  - 401 / 403 answer with the same { status } envelope as every other error.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AuthenticationEntryPoint authenticationEntryPoint,
                                                   AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/public/**", "/error").permitAll()
                        .requestMatchers("/auth/login", "/auth/otp/**", "/auth/refresh", "/auth/logout").permitAll()
                        // Signed, short-lived download links: the token in the URL is the authorization.
                        .requestMatchers(HttpMethod.GET, "/files/**").permitAll()
                        // WebSocket handshake: browsers cannot send headers on it, so the access token is checked
                        // in the STOMP CONNECT frame instead (StompAuthInterceptor).
                        .requestMatchers("/ws").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /** The "role" claim (e.g. DOCTOR) becomes the authority ROLE_DOCTOR, which hasRole('DOCTOR') checks. */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenService.CLAIM_ROLE);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** BCrypt today; the {id} prefix lets the algorithm be upgraded later without breaking stored hashes. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** No token, or an invalid/expired one. */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(MessageUtils messageUtils) {
        return (request, response, ex) ->
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, messageUtils.getMessage("auth.required"));
    }

    /** Valid token, but the role may not call this endpoint (or a service refused the action). */
    @Bean
    public AccessDeniedHandler accessDeniedHandler(MessageUtils messageUtils) {
        return (request, response, ex) ->
                writeError(response, HttpServletResponse.SC_FORBIDDEN, messageUtils.getMessage("auth.forbidden"));
    }

    /** Messages are fixed texts from messages.properties without quotes, so plain concatenation is safe. */
    private static void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":{\"code\":" + code + ",\"message\":\"" + message + "\"}}");
    }
}
