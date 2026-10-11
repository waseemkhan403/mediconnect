package com.mediconnect.dto.doctor;

import jakarta.validation.constraints.NotNull;

/** Admin marks a doctor verified (or revokes it) after checking the council registration. */
public record DoctorVerificationRequest(

        @NotNull(message = "verified is required")
        Boolean verified

) {
}
