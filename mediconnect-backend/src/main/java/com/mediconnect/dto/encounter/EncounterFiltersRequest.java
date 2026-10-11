package com.mediconnect.dto.encounter;

import java.time.LocalDate;

/**
 * All fields optional; null means "do not filter". Dates are the appointment's local date, both inclusive.
 * Doctors always get their own encounters and patients their own FINAL ones, whatever is sent here.
 */
public record EncounterFiltersRequest(
        Long appointmentId,
        Long patientId,
        Long doctorId,
        String status,
        LocalDate dateFrom,
        LocalDate dateTo
) {
}
