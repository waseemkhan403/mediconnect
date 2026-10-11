package com.mediconnect.dto.pharmacy;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Stock received: one batch of one medicine. quantity in units (tablets, bottles...), unitPrice per unit. */
public record BatchRequest(

        @NotNull(message = "medicineId is required")
        Long medicineId,

        @NotBlank(message = "batchNumber is required")
        @Size(max = 50, message = "batchNumber must not exceed 50 characters")
        String batchNumber,

        @NotNull(message = "expiryDate is required")
        LocalDate expiryDate,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        @Max(value = 1000000, message = "quantity must not exceed 1000000")
        Integer quantity,

        @NotNull(message = "unitPrice is required")
        @DecimalMin(value = "0.00", message = "unitPrice must not be negative")
        @Digits(integer = 8, fraction = 2, message = "unitPrice may have at most 8 digits and 2 decimals")
        BigDecimal unitPrice

) {
}
