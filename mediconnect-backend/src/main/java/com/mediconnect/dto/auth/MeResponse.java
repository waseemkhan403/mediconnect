package com.mediconnect.dto.auth;

import com.mediconnect.entity.Staff;
import com.mediconnect.entity.User;

/** The signed-in user. Staff fields are null for patient logins; phone is null for staff logins. */
public record MeResponse(
        Long userId,
        String role,
        Long staffId,
        Long doctorId,
        String fullName,
        String email,
        String phone
) {
    /** Expects staff to be loaded for staff logins. */
    public static MeResponse from(User u, Long doctorId) {
        Staff s = u.getStaff();
        return new MeResponse(
                u.getId(), u.role(),
                s != null ? s.getId() : null,
                doctorId,
                s != null ? s.getFullName() : null,
                s != null ? s.getEmail() : null,
                u.getPhone());
    }
}
