package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A patient's permission for one doctor to read their uploaded records and their FINAL encounters with other
 * doctors, until expiresAt or until revoked. Never deleted: revoking sets revokedAt, so past grants stay visible.
 * Active = not revoked and not expired (computed, not stored, so it can never be stale).
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "record_consents", check = {
        @CheckConstraint(name = "ck_consents_dates", constraint = "expires_at > granted_at")
})
public class RecordConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consent_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
