package com.mediconnect.controller;

import com.mediconnect.dto.record.AccessLogFiltersRequest;
import com.mediconnect.dto.record.RecordFiltersRequest;
import com.mediconnect.dto.record.RecordUploadRequest;
import com.mediconnect.dto.record.RetractRequest;
import com.mediconnect.helper.PageableBuilder;
import com.mediconnect.security.Access;
import com.mediconnect.service.RecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Medical records (append-only). No PUT and no DELETE on purpose: records are retracted, never changed.
 * Which records a caller reaches is decided in RecordService.
 */
@RestController
@RequestMapping("/records")
@RequiredArgsConstructor
@PreAuthorize(Access.SIGNED_IN)
public class RecordController {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "id", "id",
            "recordDate", "recordDate",
            "uploadedAt", "uploadedAt");

    private final RecordService recordService;
    private final PageableBuilder pageableBuilder;

    /** POST /records/list?page=0&size=10   Body (optional): { "patientId": 1, "recordType": "LAB_REPORT", "includeRetracted": false } */
    @PostMapping("/list")
    public ResponseEntity<Object> getRecords(
            @RequestBody(required = false) RecordFiltersRequest filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "uploadedAt,desc") String[] sort) {
        return recordService.getRecords(filters, pageableBuilder.build(page, size, sort, SORT_FIELDS));
    }

    /** POST /records/access-log?page=0&size=10   Body (optional): { "recordId": 3, "patientId": 1 } : who read what. */
    @PreAuthorize(Access.ADMIN_OR_PATIENT)
    @PostMapping("/access-log")
    public ResponseEntity<Object> getAccessLog(
            @RequestBody(required = false) AccessLogFiltersRequest filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "accessedAt,desc") String[] sort) {
        return recordService.getAccessLog(filters,
                pageableBuilder.build(page, size, sort, Map.of("id", "id", "accessedAt", "accessedAt")));
    }

    /** GET /records/{id} : details (logged). */
    @GetMapping("/{id}")
    public ResponseEntity<Object> getRecordById(@PathVariable Long id) {
        return recordService.getRecordById(id);
    }

    /** GET /records/{id}/download-url : a 5-minute signed link to the file. */
    @GetMapping("/{id}/download-url")
    public ResponseEntity<Object> getDownloadLink(@PathVariable Long id) {
        return recordService.getDownloadLink(id);
    }

    /**
     * POST /records   multipart/form-data: file (PDF, JPEG or PNG, max 10 MB), patientId, title, recordType,
     * recordDate (yyyy-MM-dd, optional), notes (optional).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> addRecord(
            @Valid @ModelAttribute RecordUploadRequest request,
            @RequestPart("file") MultipartFile file) {
        return recordService.addRecord(request, file);
    }

    /** PUT /records/{id}/retract   Body: { "reason": "Uploaded the wrong file" } */
    @PutMapping("/{id}/retract")
    public ResponseEntity<Object> retractRecord(
            @PathVariable Long id,
            @Valid @RequestBody RetractRequest request) {
        return recordService.retractRecord(id, request);
    }
}
