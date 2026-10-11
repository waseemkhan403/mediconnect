package com.mediconnect.dto.encounter;

import com.mediconnect.entity.Appointment;
import com.mediconnect.entity.Encounter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** The full clinical record of one consultation. */
public record EncounterResponse(
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
        Vitals vitals,
        String chiefComplaint,
        String clinicalNotes,
        String diagnosis,
        String advice,
        LocalDate followUpDate,
        List<Medicine> prescriptions,
        Instant finalizedAt,
        List<Addendum> addenda
) {
    public record Vitals(Integer bpSystolic, Integer bpDiastolic, Integer pulseRate, BigDecimal temperatureC,
                         Integer spo2, BigDecimal weightKg, Integer heightCm) {
    }

    public record Medicine(Long id, Long medicineId, String medicineName, String dosage, String frequency,
                           Integer durationDays, String instructions) {
    }

    public record Addendum(Long id, String text, Instant addedAt) {
    }

    /** Expects appointment, patient and doctor.staff loaded; reads the lazy collections (call inside a transaction). */
    public static EncounterResponse from(Encounter e, ZoneId zone) {
        Appointment a = e.getAppointment();
        return new EncounterResponse(
                e.getId(), a.getId(),
                a.getPatient().getId(), a.getPatient().getUhid(), a.getPatient().getFullName(),
                a.getDoctor().getId(), a.getDoctor().getStaff().getFullName(), a.getDoctor().getSpecialization(),
                a.getSlotStart().atZone(zone).toLocalDate(),
                e.getStatus(),
                new Vitals(e.getBpSystolic(), e.getBpDiastolic(), e.getPulseRate(), e.getTemperatureC(),
                        e.getSpo2(), e.getWeightKg(), e.getHeightCm()),
                e.getChiefComplaint(), e.getClinicalNotes(), e.getDiagnosis(), e.getAdvice(), e.getFollowUpDate(),
                e.getPrescriptionItems().stream()
                        .map(p -> new Medicine(p.getId(),
                                p.getMedicine() != null ? p.getMedicine().getId() : null,
                                p.getMedicineName(), p.getDosage(), p.getFrequency(),
                                p.getDurationDays(), p.getInstructions()))
                        .toList(),
                e.getFinalizedAt(),
                e.getAddenda().stream()
                        .map(x -> new Addendum(x.getId(), x.getText(), x.getAddedAt()))
                        .toList());
    }
}
