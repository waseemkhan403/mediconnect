package com.mediconnect.dto.pharmacy;

import java.time.LocalDate;

/**
 * All fields optional (dates are hospital-local, inclusive). Patients always get the dispensations of the patients
 * they own; doctors those of their own encounters.
 */
public record DispensationFiltersRequest(Long encounterId, Long patientId, Long doctorId, LocalDate dateFrom,
                                         LocalDate dateTo) {
}
