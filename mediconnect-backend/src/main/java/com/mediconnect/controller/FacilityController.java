package com.mediconnect.controller;

import com.mediconnect.dto.facility.FacilityFiltersRequest;
import com.mediconnect.dto.facility.FacilityRequest;
import com.mediconnect.helper.PageableBuilder;
import com.mediconnect.security.Access;
import com.mediconnect.service.FacilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/facilities")
@RequiredArgsConstructor
@PreAuthorize(Access.STAFF)
public class FacilityController {

    private static final Map<String, String> SORT_FIELDS =
            Map.of("id", "id", "name", "name", "active", "active");

    private final FacilityService facilityService;
    private final PageableBuilder pageableBuilder;

    /** POST /facilities/list?page=0&size=10&sort=name,asc   Body (optional): { "name": "icu", "active": true } */
    @PostMapping("/list")
    public ResponseEntity<Object> getFacilities(
            @RequestBody(required = false) FacilityFiltersRequest filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String[] sort) {
        return facilityService.getFacilities(filters, pageableBuilder.build(page, size, sort, SORT_FIELDS));
    }

    /** GET /facilities/active */
    @GetMapping("/active")
    public ResponseEntity<Object> getActiveFacilities() {
        return facilityService.getActiveFacilities();
    }

    /** GET /facilities/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Object> getFacilityById(@PathVariable Long id) {
        return facilityService.getFacilityById(id);
    }

    /** POST /facilities */
    @PreAuthorize(Access.ADMIN)
    @PostMapping
    public ResponseEntity<Object> addFacility(@Valid @RequestBody FacilityRequest request) {
        return facilityService.addFacility(request);
    }

    /** PUT /facilities/{id} */
    @PreAuthorize(Access.ADMIN)
    @PutMapping("/{id}")
    public ResponseEntity<Object> updateFacility(
            @PathVariable Long id,
            @Valid @RequestBody FacilityRequest request) {
        return facilityService.updateFacility(id, request);
    }

    /** DELETE /facilities/{id} */
    @PreAuthorize(Access.ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteFacility(@PathVariable Long id) {
        return facilityService.deleteFacility(id);
    }
}
