package com.mediconnect.dto.leave;

import com.mediconnect.dto.ValidationRegex;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Apply for leave (POST) or change a PENDING leave (PUT replaces the whole request; reason left out becomes null).
 * Whole days, both dates inclusive, hospital-local.
 * staffId: admin only, to record leave for someone else (created already APPROVED). Everyone else always applies
 * for themselves and this field is ignored. It is also ignored on PUT (a leave never changes owner).
 */
public record LeaveRequest(

        Long staffId,

        @NotBlank(message = "leaveType is required")
        @Pattern(regexp = ValidationRegex.LEAVE_TYPE, message = "leaveType must be CASUAL, SICK, EARNED or OTHER")
        String leaveType,

        @NotNull(message = "startDate is required")
        LocalDate startDate,

        @NotNull(message = "endDate is required")
        LocalDate endDate,

        @Size(max = 500, message = "reason must not exceed 500 characters")
        String reason

) {
}
