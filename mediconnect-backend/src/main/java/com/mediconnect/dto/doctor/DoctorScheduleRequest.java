package com.mediconnect.dto.doctor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The doctor comes from the URL (/doctors/{id}/schedules). Cross-field rules are checked in the service:
 * endTime after startTime, validTo not before validFrom, window long enough for one slot, no overlap.
 */
public record DoctorScheduleRequest(

        @NotNull(message = "dayOfWeek is required")
        DayOfWeek dayOfWeek,

        @NotNull(message = "startTime is required")
        LocalTime startTime,

        @NotNull(message = "endTime is required")
        LocalTime endTime,

        @NotNull(message = "slotDurationMinutes is required")
        @Min(value = 5, message = "slotDurationMinutes must be at least 5")
        @Max(value = 240, message = "slotDurationMinutes must not exceed 240")
        Integer slotDurationMinutes,

        @NotNull(message = "validFrom is required")
        LocalDate validFrom,

        /** null = open-ended. */
        LocalDate validTo

) {
}
