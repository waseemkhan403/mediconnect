package com.mediconnect.dto.leave;

import java.time.LocalDate;

/**
 * All fields optional; null means "do not filter". A leave matches the dates when it overlaps dateFrom..dateTo.
 * staffId only applies for admins and reception; everyone else always gets their own leaves.
 */
public record LeaveFiltersRequest(
        Long staffId,
        String status,
        LocalDate dateFrom,
        LocalDate dateTo
) {
}
