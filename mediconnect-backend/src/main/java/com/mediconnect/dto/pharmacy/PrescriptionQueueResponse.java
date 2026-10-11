package com.mediconnect.dto.pharmacy;

import com.mediconnect.entity.Appointment;
import com.mediconnect.entity.Encounter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * A finalized prescription for the pharmacy counter. Only what the pharmacist needs: the medicines, not the
 * clinical notes or diagnosis (minimum necessary access). dispensed: at least one dispensation exists.
 */
public record PrescriptionQueueResponse(
        Long encounterId,
        Long appointmentId,
        LocalDate visitDate,
        Long patientId,
        String patientUhid,
        String patientName,
        String doctorName,
        Instant finalizedAt,
        boolean dispensed,
        List<Line> items
) {
    /** medicineId: the catalogue medicine the doctor picked, or null for a free-text line. */
    public record Line(Long prescriptionItemId, Long medicineId, String medicineName, String dosage, String frequency,
                       Integer durationDays, String instructions) {
    }

    /** Expects appointment, patient and doctor.staff loaded; reads the lazy prescription (call inside a transaction). */
    public static PrescriptionQueueResponse from(Encounter e, boolean dispensed, ZoneId zone) {
        Appointment a = e.getAppointment();
        return new PrescriptionQueueResponse(
                e.getId(), a.getId(), a.getSlotStart().atZone(zone).toLocalDate(),
                a.getPatient().getId(), a.getPatient().getUhid(), a.getPatient().getFullName(),
                a.getDoctor().getStaff().getFullName(), e.getFinalizedAt(), dispensed,
                e.getPrescriptionItems().stream()
                        .map(p -> new Line(p.getId(), p.getMedicine() != null ? p.getMedicine().getId() : null,
                                p.getMedicineName(), p.getDosage(), p.getFrequency(), p.getDurationDays(),
                                p.getInstructions()))
                        .toList());
    }
}
