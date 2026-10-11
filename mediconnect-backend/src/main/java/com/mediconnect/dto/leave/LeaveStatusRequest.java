package com.mediconnect.dto.leave;

import com.mediconnect.dto.ValidationRegex;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** APPROVED / REJECTED: admin only. CANCELLED: the person themselves, or an admin. */
public record LeaveStatusRequest(

        @NotBlank(message = "status is required")
        @Pattern(regexp = ValidationRegex.LEAVE_STATUS_CHANGE, message = "status must be APPROVED, REJECTED or CANCELLED")
        String status,

        @Size(max = 500, message = "reviewNote must not exceed 500 characters")
        String reviewNote

) {
}
