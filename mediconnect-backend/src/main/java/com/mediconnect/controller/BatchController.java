package com.mediconnect.controller;

import com.mediconnect.dto.pharmacy.BatchFiltersRequest;
import com.mediconnect.dto.pharmacy.BatchRequest;
import com.mediconnect.dto.pharmacy.BatchUpdateRequest;
import com.mediconnect.dto.pharmacy.StockAdjustmentRequest;
import com.mediconnect.helper.PageableBuilder;
import com.mediconnect.security.Access;
import com.mediconnect.service.BatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Pharmacy stock: batches received, their corrections and stock adjustments. Pharmacist and admin. */
@RestController
@RequestMapping("/batches")
@RequiredArgsConstructor
@PreAuthorize(Access.PHARMACY)
public class BatchController {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "id", "id",
            "expiryDate", "expiryDate",
            "quantityOnHand", "quantityOnHand",
            "receivedAt", "receivedAt");

    private final BatchService batchService;
    private final PageableBuilder pageableBuilder;

    /**
     * POST /batches/list?page=0&size=10&sort=expiryDate,asc
     * Body (optional): { "medicineId": 1, "expiringWithinDays": 30, "inStockOnly": true }
     */
    @PostMapping("/list")
    public ResponseEntity<Object> getBatches(
            @RequestBody(required = false) BatchFiltersRequest filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "expiryDate,asc") String[] sort) {
        return batchService.getBatches(filters, pageableBuilder.build(page, size, sort, SORT_FIELDS));
    }

    /**
     * POST /batches : stock received.
     * Body: { "medicineId": 1, "batchNumber": "DL2410A", "expiryDate": "2027-09-30", "quantity": 500, "unitPrice": 2.10 }
     */
    @PostMapping
    public ResponseEntity<Object> addBatch(@Valid @RequestBody BatchRequest request) {
        return batchService.addBatch(request);
    }

    /** POST /batches/{id}/adjustments   Body: { "quantityChange": -10, "reason": "DAMAGED", "note": "strip torn" } */
    @PostMapping("/{id}/adjustments")
    public ResponseEntity<Object> addAdjustment(@PathVariable Long id, @Valid @RequestBody StockAdjustmentRequest request) {
        return batchService.addAdjustment(id, request);
    }

    /** GET /batches/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Object> getBatchById(@PathVariable Long id) {
        return batchService.getBatchById(id);
    }

    /** GET /batches/{id}/adjustments?page=0&size=10 : newest first. */
    @GetMapping("/{id}/adjustments")
    public ResponseEntity<Object> getAdjustments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return batchService.getAdjustments(id, pageableBuilder.build(page, size,
                new String[]{"adjustedAt", "desc"}, Map.of("adjustedAt", "adjustedAt")));
    }

    /** PUT /batches/{id}   Body: { "batchNumber": "DL2410A", "expiryDate": "2027-09-30", "unitPrice": 2.25 } (label data only) */
    @PutMapping("/{id}")
    public ResponseEntity<Object> updateBatch(@PathVariable Long id, @Valid @RequestBody BatchUpdateRequest request) {
        return batchService.updateBatch(id, request);
    }

    /** DELETE /batches/{id} : a wrong entry, only while nothing has been taken from it. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteBatch(@PathVariable Long id) {
        return batchService.deleteBatch(id);
    }
}
