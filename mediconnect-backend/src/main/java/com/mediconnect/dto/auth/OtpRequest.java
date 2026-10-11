package com.mediconnect.dto.auth;

import com.mediconnect.dto.ValidationRegex;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Patient sign-in, step 1: send a one-time code to this phone. */
public record OtpRequest(

        @NotBlank(message = "phone is required")
        @Pattern(regexp = ValidationRegex.PHONE, message = "phone is invalid")
        String phone

) {
}
