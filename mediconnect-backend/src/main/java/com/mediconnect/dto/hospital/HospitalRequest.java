package com.mediconnect.dto.hospital;

import com.mediconnect.dto.ValidationRegex;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Update-only: the single hospital row is seeded by the migration, so there is no "create". */
public record HospitalRequest(

        @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must not exceed 200 characters")
        String name,

        @Size(max = 5000, message = "about must not exceed 5000 characters")
        String about,

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

        /** null = "India". */
        @Size(max = 100, message = "country must not exceed 100 characters")
        String country,

        @Pattern(regexp = ValidationRegex.PHONE, message = "phone is invalid")
        String phone,

        @Pattern(regexp = ValidationRegex.PHONE, message = "emergencyPhone is invalid")
        String emergencyPhone,

        @Email(message = "email is invalid")
        @Size(max = 150, message = "email must not exceed 150 characters")
        String email,

        @Size(max = 200, message = "website must not exceed 200 characters")
        String website,

        @Size(max = 300, message = "openingHours must not exceed 300 characters")
        String openingHours,

        /** IANA zone id (e.g. Asia/Kolkata); the service validates it with ZoneId.of. null = "Asia/Kolkata". */
        @Size(max = 50, message = "timezone must not exceed 50 characters")
        String timezone

) {
}
