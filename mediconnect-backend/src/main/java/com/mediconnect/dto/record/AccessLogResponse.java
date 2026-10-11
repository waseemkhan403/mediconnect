package com.mediconnect.dto.record;

import com.mediconnect.entity.RecordAccessLog;
import com.mediconnect.entity.User;

import java.time.Instant;

/** One read of a record. userName is the staff name (null for patient logins, and for deleted logins). */
public record AccessLogResponse(
        Long id,
        Long recordId,
        String recordTitle,
        Long patientId,
        Long userId,
        String userRole,
        String userName,
        String action,
        Instant accessedAt
) {
    /** Expects record, record.patient and user (+ staff) to be loaded. */
    public static AccessLogResponse from(RecordAccessLog l) {
        User u = l.getUser();
        return new AccessLogResponse(
                l.getId(), l.getRecord().getId(), l.getRecord().getTitle(), l.getRecord().getPatient().getId(),
                u != null ? u.getId() : null,
                u != null ? u.role() : null,
                u != null && u.getStaff() != null ? u.getStaff().getFullName() : null,
                l.getAction(), l.getAccessedAt());
    }
}
