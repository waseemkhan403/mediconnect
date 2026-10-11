package com.mediconnect.dto.staff;

/** All fields optional; null means "do not filter". */
public record StaffFiltersRequest(String name, String staffType, String status, Long departmentId) {
}
