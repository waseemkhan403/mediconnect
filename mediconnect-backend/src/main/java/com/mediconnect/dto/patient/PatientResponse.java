package com.mediconnect.dto.patient;

import com.mediconnect.entity.Patient;

import java.time.LocalDate;

public record PatientResponse(
        Long id,
        String uhid,
        String fullName,
        LocalDate dateOfBirth,
        String gender,
        String phone,
        String email,
        String bloodGroup,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String emergencyContactName,
        String emergencyContactPhone,
        boolean active
) {
    public static PatientResponse from(Patient p) {
        return new PatientResponse(
                p.getId(), p.getUhid(), p.getFullName(), p.getDateOfBirth(), p.getGender(),
                p.getPhone(), p.getEmail(), p.getBloodGroup(),
                p.getAddressLine1(), p.getAddressLine2(), p.getCity(), p.getState(), p.getPostalCode(),
                p.getEmergencyContactName(), p.getEmergencyContactPhone(), p.isActive());
    }
}
