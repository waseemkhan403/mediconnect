package com.mediconnect.dto.facility;

/** All fields optional; null means "do not filter". */
public record FacilityFiltersRequest(String name, Boolean active) {
}
