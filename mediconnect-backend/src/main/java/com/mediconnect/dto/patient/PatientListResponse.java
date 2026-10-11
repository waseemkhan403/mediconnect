package com.mediconnect.dto.patient;

import com.mediconnect.entity.Patient;

import java.time.LocalDate;

/** Table row: no address, blood group or emergency contact, to minimise personal data in list payloads. */
public record PatientListResponse(
        Long id,
        String uhid,
        String fullName,
        LocalDate dateOfBirth,
        String gender,
        String phone,
        boolean active
) {
    public static PatientListResponse from(Patient p) {
        return new PatientListResponse(
                p.getId(), p.getUhid(), p.getFullName(), p.getDateOfBirth(), p.getGender(),
                p.getPhone(), p.isActive());
    }
}
