package com.mediconnect.controller;

import com.mediconnect.dto.encounter.AddendumRequest;
import com.mediconnect.dto.encounter.EncounterCreateRequest;
import com.mediconnect.dto.encounter.EncounterFiltersRequest;
import com.mediconnect.dto.encounter.EncounterRequest;
import com.mediconnect.dto.encounter.VitalsRequest;
import com.mediconnect.helper.PageableBuilder;
import com.mediconnect.security.Access;
import com.mediconnect.service.EncounterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Clinical records of consultations. Which records each caller may touch is decided in EncounterService. */
@RestController
@RequestMapping("/encounters")
@RequiredArgsConstructor
public class EncounterController {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "id", "id",
            "date", "appointment.slotStart");

    private final EncounterService encounterService;
    private final PageableBuilder pageableBuilder;

    /**
     * POST /encounters/list?page=0&size=10&sort=date,desc
     * Body (optional): { "appointmentId": 5, "patientId": 2, "doctorId": 1, "status": "FINAL",
     *                    "dateFrom": "2026-10-01", "dateTo": "2026-10-31" }
     */
    @PreAuthorize(Access.CLINICAL_RECORDS)
    @PostMapping("/list")
    public ResponseEntity<Object> getEncounters(
            @RequestBody(required = false) EncounterFiltersRequest filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "date,desc") String[] sort) {
        return encounterService.getEncounters(filters, pageableBuilder.build(page, size, sort, SORT_FIELDS));
    }

    /** GET /encounters/{id} : vitals, notes, prescription and addenda. */
    @PreAuthorize(Access.CLINICAL_RECORDS)
    @GetMapping("/{id}")
    public ResponseEntity<Object> getEncounterById(@PathVariable Long id) {
        return encounterService.getEncounterById(id);
    }

    /** POST /encounters   Body: { "appointmentId": 5 } : once the patient is checked in. */
    @PreAuthorize(Access.CLINICIANS)
    @PostMapping
    public ResponseEntity<Object> addEncounter(@Valid @RequestBody EncounterCreateRequest request) {
        return encounterService.addEncounter(request);
    }

    /** POST /encounters/{id}/addenda   Body: { "text": "..." } : corrections after finalizing. */
    @PreAuthorize(Access.DOCTOR)
    @PostMapping("/{id}/addenda")
    public ResponseEntity<Object> addAddendum(
            @PathVariable Long id,
            @Valid @RequestBody AddendumRequest request) {
        return encounterService.addAddendum(id, request);
    }

    /**
     * PUT /encounters/{id}   Body: { "chiefComplaint": "...", "clinicalNotes": "...", "diagnosis": "...",
     *   "advice": "...", "followUpDate": "2026-10-26",
     *   "prescriptions": [ { "medicineName": "Paracetamol", "dosage": "500 mg", "frequency": "1-0-1",
     *                        "durationDays": 3, "instructions": "after food" } ] }
     */
    @PreAuthorize(Access.DOCTOR)
    @PutMapping("/{id}")
    public ResponseEntity<Object> updateEncounter(
            @PathVariable Long id,
            @Valid @RequestBody EncounterRequest request) {
        return encounterService.updateEncounter(id, request);
    }

    /** PUT /encounters/{id}/vitals   Body: { "bpSystolic": 120, "bpDiastolic": 80, "pulseRate": 72, ... } */
    @PreAuthorize(Access.CLINICIANS)
    @PutMapping("/{id}/vitals")
    public ResponseEntity<Object> updateVitals(
            @PathVariable Long id,
            @Valid @RequestBody VitalsRequest request) {
        return encounterService.updateVitals(id, request);
    }

    /** PUT /encounters/{id}/finalize : locks the record and completes the appointment. */
    @PreAuthorize(Access.DOCTOR)
    @PutMapping("/{id}/finalize")
    public ResponseEntity<Object> finalizeEncounter(@PathVariable Long id) {
        return encounterService.finalizeEncounter(id);
    }

    /** DELETE /encounters/{id} : DRAFT opened by mistake only. */
    @PreAuthorize(Access.ADMIN_OR_DOCTOR)
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteEncounter(@PathVariable Long id) {
        return encounterService.deleteEncounter(id);
    }
}
