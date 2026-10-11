package com.mediconnect.dto.encounter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A correction or later finding appended to a FINAL encounter. */
public record AddendumRequest(

        @NotBlank(message = "text is required")
        @Size(max = 2000, message = "text must not exceed 2000 characters")
        String text

) {
}
