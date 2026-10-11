package com.mediconnect.dto.auth;

import com.mediconnect.dto.ValidationRegex;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Patient sign-in, step 2: the code from step 1. Creates the patient login on first use. */
public record OtpVerifyRequest(

        @NotBlank(message = "phone is required")
        @Pattern(regexp = ValidationRegex.PHONE, message = "phone is invalid")
        String phone,

        @NotBlank(message = "otp is required")
        @Pattern(regexp = "\\d{6}", message = "otp must be 6 digits")
        String otp

) {
}
