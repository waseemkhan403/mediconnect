package com.mediconnect.controller;

import com.mediconnect.dto.ActiveStatusRequest;
import com.mediconnect.dto.patient.PatientFiltersRequest;
import com.mediconnect.dto.patient.PatientRequest;
import com.mediconnect.helper.PageableBuilder;
import com.mediconnect.security.Access;
import com.mediconnect.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/patients")
@RequiredArgsConstructor
public class PatientController {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "id", "id",
            "uhid", "uhid",
            "fullName", "fullName",
            "dateOfBirth", "dateOfBirth");

    private final PatientService patientService;
    private final PageableBuilder pageableBuilder;

    /**
     * POST /patients/list?page=0&size=10&sort=fullName,asc
     * Body (optional): { "name": "anita", "phone": "9876543210", "uhid": "UH00000001", "active": true }
     * Authorization: ADMIN / RECEPTIONIST / treating DOCTOR (added with the security module).
     */
    @PreAuthorize(Access.PATIENT_SEARCH)
    @PostMapping("/list")
    public ResponseEntity<Object> getPatients(
            @RequestBody(required = false) PatientFiltersRequest filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String[] sort) {
        return patientService.getPatients(filters, pageableBuilder.build(page, size, sort, SORT_FIELDS));
    }

    /** GET /patients/{id} */
    @PreAuthorize(Access.CARE_TEAM_OR_PATIENT)
    @GetMapping("/{id}")
    public ResponseEntity<Object> getPatientById(@PathVariable Long id) {
        return patientService.getPatientById(id);
    }

    /** POST /patients : registers a patient and generates the UHID. */
    @PreAuthorize(Access.FRONT_DESK_OR_PATIENT)
    @PostMapping
    public ResponseEntity<Object> addPatient(@Valid @RequestBody PatientRequest request) {
        return patientService.addPatient(request);
    }

    /** PUT /patients/{id} */
    @PreAuthorize(Access.FRONT_DESK_OR_PATIENT)
    @PutMapping("/{id}")
    public ResponseEntity<Object> updatePatient(
            @PathVariable Long id,
            @Valid @RequestBody PatientRequest request) {
        return patientService.updatePatient(id, request);
    }

    /** PUT /patients/{id}/status   Body: { "active": false } */
    @PreAuthorize(Access.FRONT_DESK)
    @PutMapping("/{id}/status")
    public ResponseEntity<Object> updatePatientStatus(
            @PathVariable Long id,
            @Valid @RequestBody ActiveStatusRequest request) {
        return patientService.updatePatientStatus(id, request);
    }

    /** DELETE /patients/{id} : permanent. Refused (409) once appointments or records reference the patient. */
    @PreAuthorize(Access.ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deletePatient(@PathVariable Long id) {
        return patientService.deletePatient(id);
    }
}
