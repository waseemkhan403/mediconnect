package com.mediconnect.dto.doctor;

import com.mediconnect.dto.ValidationRegex;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One request creates/updates both the Staff row (person fields) and the Doctor row (professional fields)
 * in a single transaction. The service forces staffType = DOCTOR. "verified" is not here:
 * verification is an admin action (DoctorVerificationRequest), not part of editing a profile.
 */
public record DoctorRequest(

        // ---- person (stored on staff) ----
        @NotBlank(message = "staffCode is required")
        @Size(max = 20, message = "staffCode must not exceed 20 characters")
        String staffCode,

        @NotBlank(message = "fullName is required")
        @Size(max = 150, message = "fullName must not exceed 150 characters")
        String fullName,

        @NotBlank(message = "gender is required")
        @Pattern(regexp = ValidationRegex.GENDER, message = "gender must be MALE, FEMALE or OTHER")
        String gender,

        @Past(message = "dateOfBirth must be in the past")
        LocalDate dateOfBirth,

        @NotBlank(message = "phone is required")
        @Pattern(regexp = ValidationRegex.PHONE, message = "phone is invalid")
        String phone,

        @NotBlank(message = "email is required")
        @Email(message = "email is invalid")
        @Size(max = 150, message = "email must not exceed 150 characters")
        String email,

        @Size(max = 100, message = "designation must not exceed 100 characters")
        String designation,

        @NotNull(message = "departmentId is required")
        Long departmentId,

        LocalDate joiningDate,

        // ---- professional (stored on doctors) ----
        @NotBlank(message = "specialization is required")
        @Size(max = 100, message = "specialization must not exceed 100 characters")
        String specialization,

        @Size(max = 500, message = "qualifications must not exceed 500 characters")
        String qualifications,

        @NotBlank(message = "registrationNumber is required")
        @Size(max = 50, message = "registrationNumber must not exceed 50 characters")
        String registrationNumber,

        @NotBlank(message = "registrationCouncil is required")
        @Size(max = 150, message = "registrationCouncil must not exceed 150 characters")
        String registrationCouncil,

        /** null = 0. */
        @Min(value = 0, message = "experienceYears must not be negative")
        @Max(value = 80, message = "experienceYears is unrealistic")
        Integer experienceYears,

        /** null = 0.00. */
        @DecimalMin(value = "0.00", message = "consultationFee must not be negative")
        @Digits(integer = 8, fraction = 2, message = "consultationFee allows up to 8 digits and 2 decimals")
        BigDecimal consultationFee,

        @Size(max = 10, message = "languages must not exceed 10 entries")
        List<@NotBlank(message = "language must not be blank")
             @Size(max = 50, message = "language must not exceed 50 characters") String> languages,

        @Size(max = 2000, message = "bio must not exceed 2000 characters")
        String bio

) {
}
