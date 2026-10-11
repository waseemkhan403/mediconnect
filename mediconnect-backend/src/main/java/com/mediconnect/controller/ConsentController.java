package com.mediconnect.controller;

import com.mediconnect.dto.consent.ConsentFiltersRequest;
import com.mediconnect.dto.consent.ConsentRequest;
import com.mediconnect.helper.PageableBuilder;
import com.mediconnect.security.Access;
import com.mediconnect.service.ConsentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Patients sharing their records with a doctor. Grants are revoked, never deleted. */
@RestController
@RequestMapping("/consents")
@RequiredArgsConstructor
public class ConsentController {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "id", "id",
            "grantedAt", "grantedAt",
            "expiresAt", "expiresAt");

    private final ConsentService consentService;
    private final PageableBuilder pageableBuilder;

    /** POST /consents/list   Body (optional): { "patientId": 1, "doctorId": 2, "activeOnly": true } */
    @PreAuthorize(Access.CONSENT_VIEWERS)
    @PostMapping("/list")
    public ResponseEntity<Object> getConsents(
            @RequestBody(required = false) ConsentFiltersRequest filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "grantedAt,desc") String[] sort) {
        return consentService.getConsents(filters, pageableBuilder.build(page, size, sort, SORT_FIELDS));
    }

    /** POST /consents   Body: { "patientId": 1, "doctorId": 2, "days": 30 } */
    @PreAuthorize(Access.FRONT_DESK_OR_PATIENT)
    @PostMapping
    public ResponseEntity<Object> addConsent(@Valid @RequestBody ConsentRequest request) {
        return consentService.addConsent(request);
    }

    /** PUT /consents/{id}/revoke */
    @PreAuthorize(Access.FRONT_DESK_OR_PATIENT)
    @PutMapping("/{id}/revoke")
    public ResponseEntity<Object> revokeConsent(@PathVariable Long id) {
        return consentService.revokeConsent(id);
    }
}
