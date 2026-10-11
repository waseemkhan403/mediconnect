package com.mediconnect.dto.encounter;

import com.mediconnect.entity.Appointment;
import com.mediconnect.entity.Encounter;

import java.time.LocalDate;
import java.time.ZoneId;

/** Row for lists and a patient's history: no notes, vitals or prescription. */
public record EncounterListResponse(
        Long id,
        Long appointmentId,
        Long patientId,
        String patientUhid,
        String patientName,
        Long doctorId,
        String doctorName,
        String specialization,
        LocalDate date,
        String status,
        String diagnosis,
        LocalDate followUpDate
) {
    /** Expects appointment, patient and doctor.staff to be loaded. */
    public static EncounterListResponse from(Encounter e, ZoneId zone) {
        Appointment a = e.getAppointment();
        return new EncounterListResponse(
                e.getId(), a.getId(),
                a.getPatient().getId(), a.getPatient().getUhid(), a.getPatient().getFullName(),
                a.getDoctor().getId(), a.getDoctor().getStaff().getFullName(), a.getDoctor().getSpecialization(),
                a.getSlotStart().atZone(zone).toLocalDate(),
                e.getStatus(), e.getDiagnosis(), e.getFollowUpDate());
    }
}
