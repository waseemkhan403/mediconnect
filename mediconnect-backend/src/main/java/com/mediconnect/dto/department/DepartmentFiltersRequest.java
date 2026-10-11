package com.mediconnect.dto.department;

/** All fields optional; null means "do not filter". */
public record DepartmentFiltersRequest(String name, Boolean active) {
}
