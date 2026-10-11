package com.mediconnect.dto.pharmacy;

import com.mediconnect.entity.Appointment;
import com.mediconnect.entity.Dispensation;
import com.mediconnect.entity.User;

import java.math.BigDecimal;
import java.time.Instant;

public record DispensationListResponse(
        Long id,
        Long encounterId,
        Long patientId,
        String patientUhid,
        String patientName,
        String doctorName,
        Instant dispensedAt,
        BigDecimal totalAmount,
        String dispensedByName
) {
    /** Expects encounter.appointment (+ patient, doctor.staff) and dispensedBy (+ staff) to be loaded. */
    public static DispensationListResponse from(Dispensation x) {
        Appointment a = x.getEncounter().getAppointment();
        User u = x.getDispensedBy();
        return new DispensationListResponse(x.getId(), x.getEncounter().getId(),
                a.getPatient().getId(), a.getPatient().getUhid(), a.getPatient().getFullName(),
                a.getDoctor().getStaff().getFullName(), x.getDispensedAt(), x.getTotalAmount(),
                u != null && u.getStaff() != null ? u.getStaff().getFullName() : null);
    }
}
