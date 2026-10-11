package com.mediconnect.dto.appointment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Free slots of one doctor on one local date. Taken and past slots are left out. */
public record DoctorSlotsResponse(
        Long doctorId,
        LocalDate date,
        List<Slot> slots
) {
    public record Slot(LocalTime startTime, LocalTime endTime) {
    }
}
