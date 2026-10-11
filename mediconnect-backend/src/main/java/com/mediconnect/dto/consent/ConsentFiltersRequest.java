package com.mediconnect.dto.consent;

/**
 * All fields optional. activeOnly (default false) hides revoked and expired grants.
 * Patients always get their own patients' consents; doctors the consents given to them.
 */
public record ConsentFiltersRequest(Long patientId, Long doctorId, Boolean activeOnly) {
}
