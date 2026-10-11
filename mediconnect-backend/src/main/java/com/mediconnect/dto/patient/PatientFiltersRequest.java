package com.mediconnect.dto.patient;

/** All fields optional; null means "do not filter". phone and uhid match exactly; name is a contains-search. */
public record PatientFiltersRequest(String name, String phone, String uhid, Boolean active) {
}
