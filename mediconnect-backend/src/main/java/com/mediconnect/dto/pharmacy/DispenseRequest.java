package com.mediconnect.dto.pharmacy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Hand over medicines for a FINAL encounter's prescription. Each prescription line at most once per request. */
public record DispenseRequest(

        @NotNull(message = "encounterId is required")
        Long encounterId,

        @NotEmpty(message = "items must not be empty")
        @Size(max = 50, message = "items must not exceed 50 lines")
        List<@Valid @NotNull(message = "items must not contain null") DispenseItemRequest> items,

        @Size(max = 500, message = "notes must not exceed 500 characters")
        String notes

) {
}
