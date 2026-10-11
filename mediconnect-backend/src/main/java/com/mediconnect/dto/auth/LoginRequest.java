package com.mediconnect.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** Staff sign-in. */
public record LoginRequest(

        @NotBlank(message = "email is required")
        String email,

        @NotBlank(message = "password is required")
        String password

) {
}
