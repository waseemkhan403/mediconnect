package com.mediconnect.dto.user;

/** All fields optional; null means "do not filter". role: a staff type, or PATIENT. */
public record UserFiltersRequest(String role, Boolean active) {
}
