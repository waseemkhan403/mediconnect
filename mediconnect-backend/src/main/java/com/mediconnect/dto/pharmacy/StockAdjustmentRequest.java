package com.mediconnect.dto.pharmacy;

import com.mediconnect.dto.ValidationRegex;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * quantityChange: negative removes units, positive adds them (never 0).
 * DAMAGED and EXPIRED must be negative, RETURNED positive; CORRECTION and OTHER either way (OTHER needs a note).
 */
public record StockAdjustmentRequest(

        @NotNull(message = "quantityChange is required")
        @Min(value = -1000000, message = "quantityChange must be between -1000000 and 1000000")
        @Max(value = 1000000, message = "quantityChange must be between -1000000 and 1000000")
        Integer quantityChange,

        @NotBlank(message = "reason is required")
        @Pattern(regexp = ValidationRegex.STOCK_ADJUSTMENT_REASON,
                message = "reason must be DAMAGED, EXPIRED, RETURNED, CORRECTION or OTHER")
        String reason,

        @Size(max = 300, message = "note must not exceed 300 characters")
        String note

) {
}
