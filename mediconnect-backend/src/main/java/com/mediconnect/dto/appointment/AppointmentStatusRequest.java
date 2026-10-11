package com.mediconnect.dto.appointment;

import com.mediconnect.dto.ValidationRegex;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Move an appointment to its next status. The service decides which moves are allowed from the current one. */
public record AppointmentStatusRequest(

        @NotBlank(message = "status is required")
        @Pattern(regexp = ValidationRegex.APPOINTMENT_STATUS_CHANGE,
                message = "status must be CONFIRMED, CHECKED_IN, IN_CONSULTATION, COMPLETED, CANCELLED or NO_SHOW")
        String status,

        /** Only used with CANCELLED; optional. */
        @Size(max = 500, message = "cancellationReason must not exceed 500 characters")
        String cancellationReason

) {
}
