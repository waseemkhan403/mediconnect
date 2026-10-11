package com.mediconnect.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Short-lived signed download links, the same idea as S3 pre-signed URLs: the access check happens when the link is
 * issued, and the link itself proves it (HMAC-SHA256 over record id, user id and expiry). A browser can then open
 * it with a plain <a href> or <img src>, which cannot send an Authorization header.
 * Token: {recordId}.{userId}.{expiresEpochSeconds}.{signature}
 */
@Component
public class DownloadLinks {

    public static final Duration TTL = Duration.ofMinutes(5);
    /** Separates these signatures from anything else signed with the same key. */
    private static final String PURPOSE = "record-download:";

    private final SecretKey key;

    public DownloadLinks(SecretKey jwtSecretKey) {
        this.key = jwtSecretKey;
    }

    public record Grant(long recordId, long userId) {
    }

    public String create(long recordId, long userId, Instant expiresAt) {
        String payload = recordId + "." + userId + "." + expiresAt.getEpochSecond();
        return payload + "." + sign(payload);
    }

    /** The grant if the token is genuine and not expired; empty otherwise. */
    public Optional<Grant> verify(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 4) {
            return Optional.empty();
        }
        String payload = parts[0] + "." + parts[1] + "." + parts[2];
        // Constant-time comparison, so response timing does not leak how much of a forged signature was right.
        boolean genuine = MessageDigest.isEqual(
                sign(payload).getBytes(StandardCharsets.US_ASCII), parts[3].getBytes(StandardCharsets.US_ASCII));
        if (!genuine) {
            return Optional.empty();
        }
        try {
            if (Instant.ofEpochSecond(Long.parseLong(parts[2])).isBefore(Instant.now())) {
                return Optional.empty();
            }
            return Optional.of(new Grant(Long.parseLong(parts[0]), Long.parseLong(parts[1])));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] signature = mac.doFinal((PURPOSE + payload).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is always available in the JDK", e);
        }
    }
}
