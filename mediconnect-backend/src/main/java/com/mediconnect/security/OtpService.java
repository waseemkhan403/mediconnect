package com.mediconnect.security;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.exception.TooManyRequestsException;
import com.mediconnect.util.Hashes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-time sign-in codes for patients. Kept in memory: they live 5 minutes, so losing them on restart only means
 * asking for a new code, and no table is needed (this app runs as a single instance; several instances would
 * need a shared store such as Redis).
 * Rules: 6 digits, valid 5 minutes, single use, 5 wrong tries burn the code, a new code at most once a minute.
 * DEVELOPMENT: there is no SMS provider, so the code is written to the server log.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OtpService {

    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final Duration RESEND_AFTER = Duration.ofSeconds(60);
    static final int MAX_ATTEMPTS = 5;

    private record Pending(String codeHash, Instant sentAt, Instant expiresAt, int attemptsLeft) {
    }

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final MessageUtils messageUtils;

    public void send(String phone) {
        Instant now = Instant.now();
        pending.values().removeIf(p -> p.expiresAt().isBefore(now));

        Pending previous = pending.get(phone);
        if (previous != null && previous.sentAt().plus(RESEND_AFTER).isAfter(now)) {
            long wait = Duration.between(now, previous.sentAt().plus(RESEND_AFTER)).toSeconds() + 1;
            throw new TooManyRequestsException(messageUtils.getMessage("auth.otp.wait", String.valueOf(wait)));
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        pending.put(phone, new Pending(Hashes.sha256(code), now, now.plus(CODE_TTL), MAX_ATTEMPTS));
        log.info("OTP for {} is {} (development: no SMS provider configured)", phone, code);
    }

    /** True once for the right code; the code is then used up. Atomic per phone, so two guesses cannot race. */
    public boolean verify(String phone, String code) {
        boolean[] matched = {false};
        pending.computeIfPresent(phone, (key, p) -> {
            if (p.expiresAt().isBefore(Instant.now())) {
                return null;
            }
            if (p.codeHash().equals(Hashes.sha256(code))) {
                matched[0] = true;
                return null;
            }
            return p.attemptsLeft() > 1
                    ? new Pending(p.codeHash(), p.sentAt(), p.expiresAt(), p.attemptsLeft() - 1)
                    : null;
        });
        return matched[0];
    }
}
