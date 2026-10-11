package com.mediconnect.dto.encounter;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One medicine. frequency e.g. "1-0-1" (morning-noon-night) or "twice daily".
 * medicineId (optional) links the line to an active catalogue medicine; medicineName is still required.
 */
public record PrescriptionItemRequest(

        Long medicineId,

        @NotBlank(message = "medicineName is required")
        @Size(max = 200, message = "medicineName must not exceed 200 characters")
        String medicineName,

        @Size(max = 100, message = "dosage must not exceed 100 characters")
        String dosage,

        @Size(max = 50, message = "frequency must not exceed 50 characters")
        String frequency,

        @Min(value = 1, message = "durationDays must be at least 1")
        @Max(value = 365, message = "durationDays must not exceed 365")
        Integer durationDays,

        @Size(max = 300, message = "instructions must not exceed 300 characters")
        String instructions

) {
}
