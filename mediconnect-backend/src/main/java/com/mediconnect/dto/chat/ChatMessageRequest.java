package com.mediconnect.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Text only. Documents go through /records, which checks file types and logs access. */
public record ChatMessageRequest(

        @NotBlank(message = "body is required")
        @Size(max = 2000, message = "body must be at most 2000 characters")
        String body

) {
}
