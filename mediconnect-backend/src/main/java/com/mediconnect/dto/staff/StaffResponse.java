package com.mediconnect.dto.staff;

import com.mediconnect.entity.Department;
import com.mediconnect.entity.Staff;

import java.time.LocalDate;

public record StaffResponse(
        Long id,
        String staffCode,
        String fullName,
        String gender,
        LocalDate dateOfBirth,
        String phone,
        String email,
        String staffType,
        String designation,
        Long departmentId,
        String departmentName,
        LocalDate joiningDate,
        String status
) {
    /** Expects Staff.department to be loaded (use StaffRepository.findDetailById / findAllWithFilters). */
    public static StaffResponse from(Staff s) {
        Department d = s.getDepartment();
        return new StaffResponse(
                s.getId(), s.getStaffCode(), s.getFullName(), s.getGender(), s.getDateOfBirth(),
                s.getPhone(), s.getEmail(), s.getStaffType(), s.getDesignation(),
                d != null ? d.getId() : null,
                d != null ? d.getName() : null,
                s.getJoiningDate(), s.getStatus());
    }
}
