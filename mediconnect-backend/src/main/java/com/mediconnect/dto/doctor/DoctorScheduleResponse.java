package com.mediconnect.dto.doctor;

import com.mediconnect.entity.DoctorSchedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

public record DoctorScheduleResponse(
        Long id,
        Long doctorId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Integer slotDurationMinutes,
        LocalDate validFrom,
        LocalDate validTo
) {
    /** Reading doctor.getId() on a lazy proxy does not trigger a query, so no fetch join is needed. */
    public static DoctorScheduleResponse from(DoctorSchedule s) {
        return new DoctorScheduleResponse(
                s.getId(), s.getDoctor().getId(), s.getDayOfWeek(), s.getStartTime(), s.getEndTime(),
                s.getSlotDurationMinutes(), s.getValidFrom(), s.getValidTo());
    }
}
