package com.mediconnect.dto.leave;

import com.mediconnect.entity.Staff;
import com.mediconnect.entity.StaffLeave;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Used for lists too (the row is already small). days counts calendar days, both ends included. */
public record LeaveResponse(
        Long id,
        Long staffId,
        String staffCode,
        String staffName,
        String staffType,
        String leaveType,
        LocalDate startDate,
        LocalDate endDate,
        long days,
        String reason,
        String status,
        String reviewNote
) {
    /** Expects staff to be loaded (StaffLeaveRepository.findDetailById / findAllWithFilters). */
    public static LeaveResponse from(StaffLeave l) {
        Staff s = l.getStaff();
        return new LeaveResponse(
                l.getId(), s.getId(), s.getStaffCode(), s.getFullName(), s.getStaffType(),
                l.getLeaveType(), l.getStartDate(), l.getEndDate(),
                ChronoUnit.DAYS.between(l.getStartDate(), l.getEndDate()) + 1,
                l.getReason(), l.getStatus(), l.getReviewNote());
    }
}
