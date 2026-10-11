package com.mediconnect.dto.pharmacy;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * One prescription line to fill. medicineId: the catalogue medicine handed over; leave it out to use the medicine
 * the doctor linked to the line (required when the line is free text). quantity in units (tablets, bottles...).
 */
public record DispenseItemRequest(

        @NotNull(message = "prescriptionItemId is required")
        Long prescriptionItemId,

        Long medicineId,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        @Max(value = 10000, message = "quantity must not exceed 10000")
        Integer quantity

) {
}
