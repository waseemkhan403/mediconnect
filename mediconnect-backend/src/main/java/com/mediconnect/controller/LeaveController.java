package com.mediconnect.controller;

import com.mediconnect.dto.leave.LeaveFiltersRequest;
import com.mediconnect.dto.leave.LeaveRequest;
import com.mediconnect.dto.leave.LeaveStatusRequest;
import com.mediconnect.helper.PageableBuilder;
import com.mediconnect.security.Access;
import com.mediconnect.service.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Staff leave. Who may see or change which leave is decided in LeaveService. */
@RestController
@RequestMapping("/leaves")
@RequiredArgsConstructor
@PreAuthorize(Access.STAFF)
public class LeaveController {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "id", "id",
            "startDate", "startDate",
            "status", "status");

    private final LeaveService leaveService;
    private final PageableBuilder pageableBuilder;

    /**
     * POST /leaves/list?page=0&size=10&sort=startDate,asc
     * Body (optional): { "staffId": 2, "status": "PENDING", "dateFrom": "2026-10-01", "dateTo": "2026-10-31" }
     * Admins and reception see everyone; other staff only their own.
     */
    @PostMapping("/list")
    public ResponseEntity<Object> getLeaves(
            @RequestBody(required = false) LeaveFiltersRequest filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "startDate,desc") String[] sort) {
        return leaveService.getLeaves(filters, pageableBuilder.build(page, size, sort, SORT_FIELDS));
    }

    /** GET /leaves/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Object> getLeaveById(@PathVariable Long id) {
        return leaveService.getLeaveById(id);
    }

    /** GET /leaves/{id}/appointments : the doctor's bookings that fall inside this leave (to reschedule). */
    @PreAuthorize(Access.FRONT_DESK)
    @GetMapping("/{id}/appointments")
    public ResponseEntity<Object> getAffectedAppointments(@PathVariable Long id) {
        return leaveService.getAffectedAppointments(id);
    }

    /** POST /leaves   Body: { "leaveType": "SICK", "startDate": "2026-10-20", "endDate": "2026-10-22", "reason": "..." } */
    @PostMapping
    public ResponseEntity<Object> addLeave(@Valid @RequestBody LeaveRequest request) {
        return leaveService.addLeave(request);
    }

    /** PUT /leaves/{id} : only while PENDING. */
    @PutMapping("/{id}")
    public ResponseEntity<Object> updateLeave(
            @PathVariable Long id,
            @Valid @RequestBody LeaveRequest request) {
        return leaveService.updateLeave(id, request);
    }

    /** PUT /leaves/{id}/status   Body: { "status": "APPROVED", "reviewNote": "..." } */
    @PutMapping("/{id}/status")
    public ResponseEntity<Object> updateLeaveStatus(
            @PathVariable Long id,
            @Valid @RequestBody LeaveStatusRequest request) {
        return leaveService.updateLeaveStatus(id, request);
    }

    /** DELETE /leaves/{id} : mistaken entries only; cancel real leave instead. */
    @PreAuthorize(Access.ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteLeave(@PathVariable Long id) {
        return leaveService.deleteLeave(id);
    }
}
