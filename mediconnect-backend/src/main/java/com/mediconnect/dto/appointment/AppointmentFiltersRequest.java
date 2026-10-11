package com.mediconnect.dto.appointment;

import java.time.LocalDate;

/**
 * All fields optional; null means "do not filter". dateFrom/dateTo are local hospital dates, both inclusive.
 * A patient's or a doctor's dashboard is this list filtered by patientId or doctorId.
 */
public record AppointmentFiltersRequest(
        Long doctorId,
        Long patientId,
        String status,
        LocalDate dateFrom,
        LocalDate dateTo
) {
}
