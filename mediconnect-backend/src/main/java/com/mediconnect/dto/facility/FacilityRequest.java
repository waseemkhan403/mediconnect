package com.mediconnect.dto.facility;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FacilityRequest(

        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must not exceed 100 characters")
        String name,

        @Size(max = 500, message = "description must not exceed 500 characters")
        String description,

        /** null = true on create, unchanged on update. */
        Boolean active

) {
}
