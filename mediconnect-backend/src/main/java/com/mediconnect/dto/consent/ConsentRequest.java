package com.mediconnect.dto.consent;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Share a patient's records and past consultations with one doctor. days null = 30. */
public record ConsentRequest(

        @NotNull(message = "patientId is required")
        Long patientId,

        @NotNull(message = "doctorId is required")
        Long doctorId,

        @Min(value = 1, message = "days must be between 1 and 365")
        @Max(value = 365, message = "days must be between 1 and 365")
        Integer days

) {
}
