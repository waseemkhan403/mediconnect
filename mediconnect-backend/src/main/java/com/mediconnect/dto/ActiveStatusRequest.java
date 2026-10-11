package com.mediconnect.dto;

import jakarta.validation.constraints.NotNull;

/** Activate / deactivate a record. Used instead of deleting people who have history. */
public record ActiveStatusRequest(

        @NotNull(message = "active is required")
        Boolean active

) {
}
