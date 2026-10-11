package com.mediconnect.dto.pharmacy;

/** All fields optional. pendingOnly (default true): only prescriptions with nothing dispensed yet. */
public record PrescriptionQueueFiltersRequest(Long patientId, Boolean pendingOnly) {
}
