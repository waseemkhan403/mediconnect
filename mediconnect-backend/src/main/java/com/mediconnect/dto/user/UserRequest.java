package com.mediconnect.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin creates a login for an existing staff member. The login email and the role come from the staff row.
 * Patient logins are never created here: patients create their own by signing in with a one-time code.
 */
public record UserRequest(

        @NotNull(message = "staffId is required")
        Long staffId,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be 8 to 72 characters")
        String password

) {
}
