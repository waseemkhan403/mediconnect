package com.mediconnect.dto.appointment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Book a slot. date + startTime are LOCAL to the hospital (exactly what GET /public/doctors/{id}/slots returns),
 * so clients never deal with timezones. The service checks that this is a real, free, future slot of the doctor.
 * patientId comes from the body until login exists; then it will come from the logged-in user.
 */
public record AppointmentRequest(

        @NotNull(message = "patientId is required")
        Long patientId,

        @NotNull(message = "doctorId is required")
        Long doctorId,

        @NotNull(message = "date is required")
        LocalDate date,

        @NotNull(message = "startTime is required")
        LocalTime startTime,

        @Size(max = 500, message = "reason must not exceed 500 characters")
        String reason

) {
}
