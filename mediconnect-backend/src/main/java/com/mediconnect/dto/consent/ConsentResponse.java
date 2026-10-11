package com.mediconnect.dto.consent;

import com.mediconnect.entity.RecordConsent;

import java.time.Instant;

public record ConsentResponse(
        Long id,
        Long patientId,
        String patientUhid,
        String patientName,
        Long doctorId,
        String doctorName,
        Instant grantedAt,
        Instant expiresAt,
        Instant revokedAt,
        boolean active
) {
    /** Expects patient and doctor.staff to be loaded. */
    public static ConsentResponse from(RecordConsent c, Instant now) {
        return new ConsentResponse(
                c.getId(), c.getPatient().getId(), c.getPatient().getUhid(), c.getPatient().getFullName(),
                c.getDoctor().getId(), c.getDoctor().getStaff().getFullName(),
                c.getGrantedAt(), c.getExpiresAt(), c.getRevokedAt(), c.isActive(now));
    }
}
