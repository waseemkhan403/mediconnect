package com.mediconnect.dto.chat;

import com.mediconnect.entity.Appointment;
import com.mediconnect.entity.ChatAccessLog;
import com.mediconnect.entity.User;

import java.time.Instant;

/** One admin access to a chat. userName is the admin's staff name (null if that login was deleted). */
public record ChatAccessLogResponse(
        Long id,
        Long threadId,
        Long patientId,
        String patientName,
        String doctorName,
        Long userId,
        String userName,
        String action,
        Instant accessedAt
) {
    /** Expects thread.encounter.appointment (+ patient, doctor.staff) and user (+ staff) to be loaded. */
    public static ChatAccessLogResponse from(ChatAccessLog l) {
        Appointment a = l.getThread().getEncounter().getAppointment();
        User u = l.getUser();
        return new ChatAccessLogResponse(
                l.getId(), l.getThread().getId(), a.getPatient().getId(), a.getPatient().getFullName(),
                a.getDoctor().getStaff().getFullName(),
                u != null ? u.getId() : null,
                u != null && u.getStaff() != null ? u.getStaff().getFullName() : null,
                l.getAction(), l.getAccessedAt());
    }
}
