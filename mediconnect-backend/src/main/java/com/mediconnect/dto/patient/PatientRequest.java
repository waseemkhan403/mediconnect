package com.mediconnect.dto.patient;

import com.mediconnect.dto.ValidationRegex;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** No uhid here: the server generates it. */
public record PatientRequest(

        @NotBlank(message = "fullName is required")
        @Size(max = 150, message = "fullName must not exceed 150 characters")
        String fullName,

        @NotNull(message = "dateOfBirth is required")
        @Past(message = "dateOfBirth must be in the past")
        LocalDate dateOfBirth,

        @NotBlank(message = "gender is required")
        @Pattern(regexp = ValidationRegex.GENDER, message = "gender must be MALE, FEMALE or OTHER")
        String gender,

        @NotBlank(message = "phone is required")
        @Pattern(regexp = ValidationRegex.PHONE, message = "phone is invalid")
        String phone,

        @Email(message = "email is invalid")
        @Size(max = 150, message = "email must not exceed 150 characters")
        String email,

        @Pattern(regexp = ValidationRegex.BLOOD_GROUP,
                message = "bloodGroup must be one of A_POSITIVE, A_NEGATIVE, B_POSITIVE, B_NEGATIVE, AB_POSITIVE, AB_NEGATIVE, O_POSITIVE, O_NEGATIVE")
        String bloodGroup,

        @Size(max = 200, message = "addressLine1 must not exceed 200 characters")
        String addressLine1,

        @Size(max = 200, message = "addressLine2 must not exceed 200 characters")
        String addressLine2,

        @Size(max = 100, message = "city must not exceed 100 characters")
        String city,

        @Size(max = 100, message = "state must not exceed 100 characters")
        String state,

        @Size(max = 20, message = "postalCode must not exceed 20 characters")
        String postalCode,

        @Size(max = 150, message = "emergencyContactName must not exceed 150 characters")
        String emergencyContactName,

        @Pattern(regexp = ValidationRegex.PHONE, message = "emergencyContactPhone is invalid")
        String emergencyContactPhone

) {
}
