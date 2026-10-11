package com.mediconnect.dto.hospital;

import com.mediconnect.entity.Hospital;

public record HospitalResponse(
        Long id,
        String name,
        String about,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country,
        String phone,
        String emergencyPhone,
        String email,
        String website,
        String openingHours,
        String timezone
) {
    public static HospitalResponse from(Hospital h) {
        return new HospitalResponse(
                h.getId(), h.getName(), h.getAbout(),
                h.getAddressLine1(), h.getAddressLine2(), h.getCity(), h.getState(),
                h.getPostalCode(), h.getCountry(),
                h.getPhone(), h.getEmergencyPhone(), h.getEmail(), h.getWebsite(),
                h.getOpeningHours(), h.getTimezone());
    }
}
