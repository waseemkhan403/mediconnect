package com.mediconnect.controller;

import com.mediconnect.dto.doctor.DoctorScheduleRequest;
import com.mediconnect.security.Access;
import com.mediconnect.service.DoctorScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Schedules are always reached through their doctor. Admins edit any schedule, doctors only their own. */
@RestController
@RequestMapping("/doctors/{doctorId}/schedules")
@RequiredArgsConstructor
@PreAuthorize(Access.STAFF)
public class DoctorScheduleController {

    private final DoctorScheduleService scheduleService;

    /** GET /doctors/{doctorId}/schedules : ordered Monday to Sunday. */
    @GetMapping
    public ResponseEntity<Object> getSchedules(@PathVariable Long doctorId) {
        return scheduleService.getSchedules(doctorId);
    }

    /** POST /doctors/{doctorId}/schedules */
    @PreAuthorize(Access.ADMIN_OR_DOCTOR)
    @PostMapping
    public ResponseEntity<Object> addSchedule(
            @PathVariable Long doctorId,
            @Valid @RequestBody DoctorScheduleRequest request) {
        return scheduleService.addSchedule(doctorId, request);
    }

    /** PUT /doctors/{doctorId}/schedules/{scheduleId} */
    @PreAuthorize(Access.ADMIN_OR_DOCTOR)
    @PutMapping("/{scheduleId}")
    public ResponseEntity<Object> updateSchedule(
            @PathVariable Long doctorId,
            @PathVariable Long scheduleId,
            @Valid @RequestBody DoctorScheduleRequest request) {
        return scheduleService.updateSchedule(doctorId, scheduleId, request);
    }

    /** DELETE /doctors/{doctorId}/schedules/{scheduleId} */
    @PreAuthorize(Access.ADMIN_OR_DOCTOR)
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Object> deleteSchedule(
            @PathVariable Long doctorId,
            @PathVariable Long scheduleId) {
        return scheduleService.deleteSchedule(doctorId, scheduleId);
    }
}
