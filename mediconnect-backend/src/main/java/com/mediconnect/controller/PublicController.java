package com.mediconnect.controller;

import com.mediconnect.dto.doctor.DoctorFiltersRequest;
import com.mediconnect.helper.PageableBuilder;
import com.mediconnect.service.AppointmentService;
import com.mediconnect.service.DepartmentService;
import com.mediconnect.service.DoctorService;
import com.mediconnect.service.FacilityService;
import com.mediconnect.service.HospitalService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Everything the public website shows without logging in. Keeping these under /public means the
 * security module can simply permit /public/** and protect the rest.
 * Only safe data lives here: no contact details of doctors, no unverified or inactive doctors.
 */
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController {

    private final HospitalService hospitalService;
    private final DepartmentService departmentService;
    private final FacilityService facilityService;
    private final DoctorService doctorService;
    private final AppointmentService appointmentService;
    private final PageableBuilder pageableBuilder;

    /** GET /public/hospital */
    @GetMapping("/hospital")
    public ResponseEntity<Object> getHospital() {
        return hospitalService.getHospital();
    }

    /** GET /public/departments : active departments only. */
    @GetMapping("/departments")
    public ResponseEntity<Object> getDepartments() {
        return departmentService.getActiveDepartments();
    }

    /** GET /public/facilities : active facilities only. */
    @GetMapping("/facilities")
    public ResponseEntity<Object> getFacilities() {
        return facilityService.getActiveFacilities();
    }

    /**
     * POST /public/doctors/list?page=0&size=10&sort=fullName,asc
     * The server forces verified = true and status = ACTIVE, whatever the body says.
     */
    @PostMapping("/doctors/list")
    public ResponseEntity<Object> getDoctors(
            @RequestBody(required = false) DoctorFiltersRequest filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String[] sort) {
        return doctorService.getPublicDoctors(
                filters, pageableBuilder.build(page, size, sort, DoctorController.SORT_FIELDS));
    }

    /**
     * GET /public/doctors/{id}/slots?date=2026-10-20
     * Free future slots of a verified, active doctor on that local date (today up to 30 days ahead).
     */
    @GetMapping("/doctors/{id}/slots")
    public ResponseEntity<Object> getDoctorSlots(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return appointmentService.getAvailableSlots(id, date);
    }
}
