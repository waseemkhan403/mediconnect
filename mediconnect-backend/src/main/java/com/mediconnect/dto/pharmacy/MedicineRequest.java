package com.mediconnect.dto.pharmacy;

import com.mediconnect.dto.ValidationRegex;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PUT replaces the whole entry: reorderLevel left out = 0; active left out = true on create, unchanged on update. */
public record MedicineRequest(

        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must not exceed 150 characters")
        String name,

        @Size(max = 150, message = "genericName must not exceed 150 characters")
        String genericName,

        @NotBlank(message = "form is required")
        @Pattern(regexp = ValidationRegex.MEDICINE_FORM, message = "form must be TABLET, CAPSULE, SYRUP, SUSPENSION, "
                + "INJECTION, OINTMENT, CREAM, DROPS, INHALER, POWDER or OTHER")
        String form,

        @Size(max = 50, message = "strength must not exceed 50 characters")
        String strength,

        @Size(max = 150, message = "manufacturer must not exceed 150 characters")
        String manufacturer,

        @Min(value = 0, message = "reorderLevel must not be negative")
        @Max(value = 1000000, message = "reorderLevel must not exceed 1000000")
        Integer reorderLevel,

        Boolean active

) {
}
