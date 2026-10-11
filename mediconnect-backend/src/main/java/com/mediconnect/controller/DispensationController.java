package com.mediconnect.controller;

import com.mediconnect.dto.pharmacy.DispensationFiltersRequest;
import com.mediconnect.dto.pharmacy.DispenseRequest;
import com.mediconnect.dto.pharmacy.PrescriptionQueueFiltersRequest;
import com.mediconnect.helper.PageableBuilder;
import com.mediconnect.security.Access;
import com.mediconnect.service.DispensationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Medicines handed over against prescriptions. Permanent: no PUT or DELETE (returns are stock adjustments). */
@RestController
@RequestMapping("/dispensations")
@RequiredArgsConstructor
public class DispensationController {

    private final DispensationService dispensationService;
    private final PageableBuilder pageableBuilder;

    /**
     * POST /dispensations/queue?page=0&size=10&sort=finalizedAt,asc
     * Body (optional): { "patientId": 1, "pendingOnly": true } : prescriptions waiting at the counter.
     */
    @PreAuthorize(Access.PHARMACY)
    @PostMapping("/queue")
    public ResponseEntity<Object> getQueue(
            @RequestBody(required = false) PrescriptionQueueFiltersRequest filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "finalizedAt,asc") String[] sort) {
        return dispensationService.getQueue(filters, pageableBuilder.build(page, size, sort,
                Map.of("id", "id", "finalizedAt", "finalizedAt")));
    }

    /**
     * POST /dispensations/list?page=0&size=10&sort=dispensedAt,desc
     * Body (optional): { "encounterId": 3, "patientId": 1, "doctorId": 2, "dateFrom": "2026-10-01", "dateTo": "2026-10-31" }
     */
    @PreAuthorize(Access.DISPENSATION_VIEWERS)
    @PostMapping("/list")
    public ResponseEntity<Object> getDispensations(
            @RequestBody(required = false) DispensationFiltersRequest filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "dispensedAt,desc") String[] sort) {
        return dispensationService.getDispensations(filters, pageableBuilder.build(page, size, sort,
                Map.of("id", "id", "dispensedAt", "dispensedAt", "totalAmount", "totalAmount")));
    }

    /**
     * POST /dispensations
     * Body: { "encounterId": 3, "items": [ { "prescriptionItemId": 7, "medicineId": 1, "quantity": 10 } ],
     *         "notes": "rest when stock arrives" }
     * medicineId may be left out when the doctor linked the line to a catalogue medicine.
     */
    @PreAuthorize(Access.PHARMACIST)
    @PostMapping
    public ResponseEntity<Object> dispense(@Valid @RequestBody DispenseRequest request) {
        return dispensationService.dispense(request);
    }

    /** GET /dispensations/{id} : lines with batch, quantity and price. */
    @PreAuthorize(Access.DISPENSATION_VIEWERS)
    @GetMapping("/{id}")
    public ResponseEntity<Object> getDispensationById(@PathVariable Long id) {
        return dispensationService.getDispensationById(id);
    }
}
