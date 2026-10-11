package com.mediconnect.dto.department;

import com.mediconnect.entity.Department;

public record DepartmentResponse(Long id, String name, String description, boolean active) {

    public static DepartmentResponse from(Department d) {
        return new DepartmentResponse(d.getId(), d.getName(), d.getDescription(), d.isActive());
    }
}
