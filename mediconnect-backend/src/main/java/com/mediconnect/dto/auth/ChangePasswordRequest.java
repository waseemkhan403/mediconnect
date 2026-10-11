package com.mediconnect.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Staff change their own password. BCrypt only reads the first 72 bytes, hence the upper limit. */
public record ChangePasswordRequest(

        @NotBlank(message = "currentPassword is required")
        String currentPassword,

        @NotBlank(message = "newPassword is required")
        @Size(min = 8, max = 72, message = "newPassword must be 8 to 72 characters")
        String newPassword

) {
}
