package com.mediconnect.dto.record;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Withdraw a wrong upload. The record and file are kept; it is only hidden from normal lists. */
public record RetractRequest(

        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason must not exceed 500 characters")
        String reason

) {
}
