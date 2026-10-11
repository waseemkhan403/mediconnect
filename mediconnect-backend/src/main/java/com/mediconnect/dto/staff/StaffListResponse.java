package com.mediconnect.dto.staff;

import com.mediconnect.entity.Staff;

/** Lighter row for tables: no date of birth, no contact details. */
public record StaffListResponse(
        Long id,
        String staffCode,
        String fullName,
        String staffType,
        String designation,
        String departmentName,
        String status
) {
    public static StaffListResponse from(Staff s) {
        return new StaffListResponse(
                s.getId(), s.getStaffCode(), s.getFullName(), s.getStaffType(), s.getDesignation(),
                s.getDepartment() != null ? s.getDepartment().getName() : null,
                s.getStatus());
    }
}
