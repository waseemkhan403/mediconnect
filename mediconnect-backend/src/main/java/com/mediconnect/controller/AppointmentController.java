package com.mediconnect.controller;

import com.mediconnect.dto.appointment.AppointmentFiltersRequest;
import com.mediconnect.dto.appointment.AppointmentRequest;
import com.mediconnect.dto.appointment.AppointmentRescheduleRequest;
import com.mediconnect.dto.appointment.AppointmentStatusRequest;
import com.mediconnect.dto.appointment.AppointmentUpdateRequest;
import com.mediconnect.helper.PageableBuilder;
import com.mediconnect.security.Access;
import com.mediconnect.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Free slots are public: GET /public/doctors/{id}/slots?date=2026-10-20. */
@RestController
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "id", "id",
            "slotStart", "slotStart",
            "status", "status");

    private final AppointmentService appointmentService;
    private final PageableBuilder pageableBuilder;

    /**
     * POST /appointments/list?page=0&size=10&sort=slotStart,asc
     * Body (optional): { "doctorId": 1, "patientId": 2, "status": "CONFIRMED",
     *                    "dateFrom": "2026-10-20", "dateTo": "2026-10-31" }
     */
    @PreAuthorize(Access.CARE_TEAM_OR_PATIENT)
    @PostMapping("/list")
    public ResponseEntity<Object> getAppointments(
            @RequestBody(required = false) AppointmentFiltersRequest filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "slotStart,asc") String[] sort) {
        return appointmentService.getAppointments(filters, pageableBuilder.build(page, size, sort, SORT_FIELDS));
    }

    /** GET /appointments/{id} */
    @PreAuthorize(Access.CARE_TEAM_OR_PATIENT)
    @GetMapping("/{id}")
    public ResponseEntity<Object> getAppointmentById(@PathVariable Long id) {
        return appointmentService.getAppointmentById(id);
    }

    /** POST /appointments   Body: { "patientId": 1, "doctorId": 1, "date": "2026-10-20", "startTime": "09:15", "reason": "..." } */
    @PreAuthorize(Access.FRONT_DESK_OR_PATIENT)
    @PostMapping
    public ResponseEntity<Object> addAppointment(@Valid @RequestBody AppointmentRequest request) {
        return appointmentService.addAppointment(request);
    }

    /** PUT /appointments/{id}   Body: { "reason": "..." } */
    @PreAuthorize(Access.FRONT_DESK_OR_PATIENT)
    @PutMapping("/{id}")
    public ResponseEntity<Object> updateAppointment(
            @PathVariable Long id,
            @Valid @RequestBody AppointmentUpdateRequest request) {
        return appointmentService.updateAppointment(id, request);
    }

    /** PUT /appointments/{id}/status   Body: { "status": "CANCELLED", "cancellationReason": "..." } */
    @PreAuthorize(Access.CARE_TEAM_OR_PATIENT)
    @PutMapping("/{id}/status")
    public ResponseEntity<Object> updateAppointmentStatus(
            @PathVariable Long id,
            @Valid @RequestBody AppointmentStatusRequest request) {
        return appointmentService.updateAppointmentStatus(id, request);
    }

    /** PUT /appointments/{id}/reschedule   Body: { "date": "2026-10-21", "startTime": "10:00" } : returns the new booking. */
    @PreAuthorize(Access.FRONT_DESK_OR_PATIENT)
    @PutMapping("/{id}/reschedule")
    public ResponseEntity<Object> rescheduleAppointment(
            @PathVariable Long id,
            @Valid @RequestBody AppointmentRescheduleRequest request) {
        return appointmentService.rescheduleAppointment(id, request);
    }

    /** DELETE /appointments/{id} : mistaken entries only; cancel real bookings instead. */
    @PreAuthorize(Access.ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteAppointment(@PathVariable Long id) {
        return appointmentService.deleteAppointment(id);
    }
}
