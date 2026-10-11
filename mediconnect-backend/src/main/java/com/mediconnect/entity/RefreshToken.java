package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * One refresh token (held by the browser in an httpOnly cookie). Only its SHA-256 hash is stored, so a database
 * leak does not hand out sessions. Rotated on every use: the old row is marked rotated and a new one issued.
 * Sign-out, password changes and deactivation DELETE rows, so a rotated row showing up again can only mean that
 * someone kept a copy of an old cookie.
 * Managed only through /auth (login creates, refresh reads + rotates, logout deletes), never through a CRUD API.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Already exchanged for a newer token. Presenting it again is treated as theft. */
    @Column(name = "rotated", nullable = false)
    @Builder.Default
    private boolean rotated = false;
}
