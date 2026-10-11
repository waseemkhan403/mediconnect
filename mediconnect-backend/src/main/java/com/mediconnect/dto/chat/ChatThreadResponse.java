package com.mediconnect.dto.chat;

import com.mediconnect.entity.Appointment;
import com.mediconnect.entity.ChatThread;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** unreadCount: messages from the other side the caller has not read yet. */
public record ChatThreadResponse(
        Long id,
        Long encounterId,
        Long appointmentId,
        LocalDate visitDate,
        Long patientId,
        String patientUhid,
        String patientName,
        Long doctorId,
        String doctorName,
        Instant openedAt,
        Instant closesAt,
        Instant closedAt,
        String closedByRole,
        boolean open,
        Instant lastActivityAt,
        long unreadCount
) {
    /** Expects encounter.appointment, patient and doctor.staff to be loaded. */
    public static ChatThreadResponse from(ChatThread t, long unreadCount, ZoneId zone, Instant now) {
        Appointment a = t.getEncounter().getAppointment();
        return new ChatThreadResponse(
                t.getId(), t.getEncounter().getId(), a.getId(), a.getSlotStart().atZone(zone).toLocalDate(),
                a.getPatient().getId(), a.getPatient().getUhid(), a.getPatient().getFullName(),
                a.getDoctor().getId(), a.getDoctor().getStaff().getFullName(),
                t.getOpenedAt(), t.getClosesAt(), t.getClosedAt(), t.getClosedByRole(), t.isOpen(now),
                t.getLastActivityAt(), unreadCount);
    }
}
