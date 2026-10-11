package com.mediconnect.dto.pharmacy;

import com.mediconnect.entity.Appointment;
import com.mediconnect.entity.Dispensation;
import com.mediconnect.entity.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** A dispensation with its lines: the patient's view of what they received, and the pharmacy receipt. */
public record DispensationResponse(
        Long id,
        Long encounterId,
        Long patientId,
        String patientUhid,
        String patientName,
        String doctorName,
        Instant dispensedAt,
        String dispensedByName,
        BigDecimal totalAmount,
        String notes,
        List<Item> items
) {
    /** prescribedName: what the doctor wrote; medicineName: what was handed over. */
    public record Item(Long id, Long prescriptionItemId, String prescribedName, Long medicineId, String medicineName,
                       String form, String strength, String batchNumber, LocalDate expiryDate, int quantity,
                       BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    /** Expects the detail graph loaded; reads the lazy items (call inside a transaction). */
    public static DispensationResponse from(Dispensation x) {
        Appointment a = x.getEncounter().getAppointment();
        User u = x.getDispensedBy();
        return new DispensationResponse(x.getId(), x.getEncounter().getId(),
                a.getPatient().getId(), a.getPatient().getUhid(), a.getPatient().getFullName(),
                a.getDoctor().getStaff().getFullName(), x.getDispensedAt(),
                u != null && u.getStaff() != null ? u.getStaff().getFullName() : null,
                x.getTotalAmount(), x.getNotes(),
                x.getItems().stream()
                        .map(i -> new Item(i.getId(), i.getPrescriptionItem().getId(),
                                i.getPrescriptionItem().getMedicineName(), i.getMedicine().getId(),
                                i.getMedicine().getName(), i.getMedicine().getForm(), i.getMedicine().getStrength(),
                                i.getBatch().getBatchNumber(), i.getBatch().getExpiryDate(), i.getQuantity(),
                                i.getUnitPrice(), i.getLineTotal()))
                        .toList());
    }
}
