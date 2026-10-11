package com.mediconnect.dto.staff;

import com.mediconnect.dto.ValidationRegex;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** For non-doctor staff. Doctors are created through DoctorRequest. */
public record StaffRequest(

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

        @NotBlank(message = "staffType is required")
        @Pattern(regexp = ValidationRegex.STAFF_TYPE_NON_DOCTOR,
                message = "staffType must be NURSE, RECEPTIONIST, PHARMACIST, LAB_TECHNICIAN, ADMIN or OTHER")
        String staffType,

        @Size(max = 100, message = "designation must not exceed 100 characters")
        String designation,

        Long departmentId,

        LocalDate joiningDate,

        /** null = ACTIVE on create, unchanged on update. */
        @Pattern(regexp = ValidationRegex.STAFF_STATUS, message = "status must be ACTIVE or INACTIVE")
        String status

) {
}
