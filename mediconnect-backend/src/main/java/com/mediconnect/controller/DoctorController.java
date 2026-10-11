package com.mediconnect.controller;

import com.mediconnect.dto.ActiveStatusRequest;
import com.mediconnect.dto.doctor.DoctorFiltersRequest;
import com.mediconnect.dto.doctor.DoctorRequest;
import com.mediconnect.dto.doctor.DoctorVerificationRequest;
import com.mediconnect.helper.PageableBuilder;
import com.mediconnect.security.Access;
import com.mediconnect.service.DoctorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/doctors")
@RequiredArgsConstructor
@PreAuthorize(Access.STAFF)
public class DoctorController {

    /** "fullName" lives on the linked staff row, hence the path. */
    static final Map<String, String> SORT_FIELDS = Map.of(
            "id", "id",
            "fullName", "staff.fullName",
            "specialization", "specialization",
            "experienceYears", "experienceYears",
            "consultationFee", "consultationFee");

    private final DoctorService doctorService;
    private final PageableBuilder pageableBuilder;

    /**
     * POST /doctors/list?page=0&size=10&sort=fullName,asc
     * Body (optional): { "name": "rao", "specialization": "cardio", "departmentId": 2,
     *                    "verified": true, "status": "ACTIVE" }
     * Admin view: includes unverified and inactive doctors. Patients use POST /public/doctors/list.
     */
    @PostMapping("/list")
    public ResponseEntity<Object> getDoctors(
            @RequestBody(required = false) DoctorFiltersRequest filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String[] sort) {
        return doctorService.getDoctors(filters, pageableBuilder.build(page, size, sort, SORT_FIELDS));
    }

    /** GET /doctors/{id} : full detail including contact and registration (admin/doctor only). */
    @GetMapping("/{id}")
    public ResponseEntity<Object> getDoctorById(@PathVariable Long id) {
        return doctorService.getDoctorById(id);
    }

    /** POST /doctors : creates the staff row and the doctor row together. */
    @PreAuthorize(Access.ADMIN)
    @PostMapping
    public ResponseEntity<Object> addDoctor(@Valid @RequestBody DoctorRequest request) {
        return doctorService.addDoctor(request);
    }

    /** PUT /doctors/{id} : changing the registration resets verification. */
    @PreAuthorize(Access.ADMIN)
    @PutMapping("/{id}")
    public ResponseEntity<Object> updateDoctor(
            @PathVariable Long id,
            @Valid @RequestBody DoctorRequest request) {
        return doctorService.updateDoctor(id, request);
    }

    /** PUT /doctors/{id}/verification   Body: { "verified": true }   Authorization: ADMIN only. */
    @PreAuthorize(Access.ADMIN)
    @PutMapping("/{id}/verification")
    public ResponseEntity<Object> verifyDoctor(
            @PathVariable Long id,
            @Valid @RequestBody DoctorVerificationRequest request) {
        return doctorService.verifyDoctor(id, request);
    }

    /** PUT /doctors/{id}/status   Body: { "active": false } */
    @PreAuthorize(Access.ADMIN)
    @PutMapping("/{id}/status")
    public ResponseEntity<Object> updateDoctorStatus(
            @PathVariable Long id,
            @Valid @RequestBody ActiveStatusRequest request) {
        return doctorService.updateDoctorStatus(id, request);
    }

    /** DELETE /doctors/{id} : for mistaken entries; prefer deactivating. */
    @PreAuthorize(Access.ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteDoctor(@PathVariable Long id) {
        return doctorService.deleteDoctor(id);
    }
}
