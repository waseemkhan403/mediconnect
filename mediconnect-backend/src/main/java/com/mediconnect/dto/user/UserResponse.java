package com.mediconnect.dto.user;

import com.mediconnect.entity.Staff;
import com.mediconnect.entity.User;

/** Never contains the password hash. Staff fields are null for patient logins. */
public record UserResponse(
        Long id,
        String role,
        Long staffId,
        String staffCode,
        String fullName,
        String email,
        String phone,
        boolean active
) {
    /** Expects staff to be loaded (UserRepository.findDetailById / findAllWithFilters). */
    public static UserResponse from(User u) {
        Staff s = u.getStaff();
        return new UserResponse(
                u.getId(), u.role(),
                s != null ? s.getId() : null,
                s != null ? s.getStaffCode() : null,
                s != null ? s.getFullName() : null,
                s != null ? s.getEmail() : null,
                u.getPhone(), u.isActive());
    }
}
