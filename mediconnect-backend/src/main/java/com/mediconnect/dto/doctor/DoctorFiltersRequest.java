package com.mediconnect.dto.doctor;

/**
 * All fields optional; null means "do not filter".
 * status is the staff status: ACTIVE or INACTIVE.
 */
public record DoctorFiltersRequest(
        String name,
        String specialization,
        Long departmentId,
        Boolean verified,
        String status
) {
}
