package com.mediconnect.dto.appointment;

import com.mediconnect.entity.Appointment;
import com.mediconnect.entity.Doctor;
import com.mediconnect.entity.Patient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Used for lists too: the row is already small, so there is no separate AppointmentListResponse.
 * date/startTime/endTime are local to the hospital timezone.
 */
public record AppointmentResponse(
        Long id,
        Long patientId,
        String patientUhid,
        String patientName,
        Long doctorId,
        String doctorName,
        String specialization,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String status,
        String reason,
        String cancellationReason,
        Long rescheduledFromId
) {
    /** Expects patient and doctor.staff to be loaded (AppointmentRepository.findDetailById / findAllWithFilters). */
    public static AppointmentResponse from(Appointment a, ZoneId zone) {
        Patient p = a.getPatient();
        Doctor d = a.getDoctor();
        ZonedDateTime start = a.getSlotStart().atZone(zone);
        ZonedDateTime end = a.getSlotEnd().atZone(zone);
        return new AppointmentResponse(
                a.getId(), p.getId(), p.getUhid(), p.getFullName(),
                d.getId(), d.getStaff().getFullName(), d.getSpecialization(),
                start.toLocalDate(), start.toLocalTime(), end.toLocalTime(),
                a.getStatus(), a.getReason(), a.getCancellationReason(),
                // getId() on a lazy proxy does not trigger a query.
                a.getRescheduledFrom() != null ? a.getRescheduledFrom().getId() : null);
    }
}
