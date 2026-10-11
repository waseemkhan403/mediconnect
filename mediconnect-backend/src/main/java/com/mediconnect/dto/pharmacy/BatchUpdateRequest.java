package com.mediconnect.dto.pharmacy;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Corrects a batch's label data (typo in the number, wrong expiry, new price). Quantities are not editable here:
 * they change only through dispensing and stock adjustments, so every unit stays explained.
 */
public record BatchUpdateRequest(

        @NotBlank(message = "batchNumber is required")
        @Size(max = 50, message = "batchNumber must not exceed 50 characters")
        String batchNumber,

        @NotNull(message = "expiryDate is required")
        LocalDate expiryDate,

        @NotNull(message = "unitPrice is required")
        @DecimalMin(value = "0.00", message = "unitPrice must not be negative")
        @Digits(integer = 8, fraction = 2, message = "unitPrice may have at most 8 digits and 2 decimals")
        BigDecimal unitPrice

) {
}
