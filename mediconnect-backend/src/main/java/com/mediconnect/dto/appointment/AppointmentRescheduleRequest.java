package com.mediconnect.dto.appointment;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/** New local slot with the same doctor. To change doctor, cancel and book again. */
public record AppointmentRescheduleRequest(

        @NotNull(message = "date is required")
        LocalDate date,

        @NotNull(message = "startTime is required")
        LocalTime startTime

) {
}
