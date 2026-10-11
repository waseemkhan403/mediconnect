package com.mediconnect.controller;

import com.mediconnect.dto.hospital.HospitalRequest;
import com.mediconnect.security.Access;
import com.mediconnect.service.HospitalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/hospital")
@RequiredArgsConstructor
@PreAuthorize(Access.STAFF)
public class HospitalController {

    private final HospitalService hospitalService;

    /**
     * POST /hospital
     * Creates the hospital. Only one can exist, so this returns 409 while one is already there.
     * Authorization: ADMIN (added with the security module).
     */
    @PreAuthorize(Access.ADMIN)
    @PostMapping
    public ResponseEntity<Object> addHospital(@Valid @RequestBody HospitalRequest request) {
        return hospitalService.addHospital(request);
    }

    /** GET /hospital  (patients use GET /public/hospital) */
    @GetMapping
    public ResponseEntity<Object> getHospital() {
        return hospitalService.getHospital();
    }

    /** PUT /hospital : replaces all fields. */
    @PreAuthorize(Access.ADMIN)
    @PutMapping
    public ResponseEntity<Object> updateHospital(@Valid @RequestBody HospitalRequest request) {
        return hospitalService.updateHospital(request);
    }

    /** DELETE /hospital : removes the hospital so a new one can be created. */
    @PreAuthorize(Access.ADMIN)
    @DeleteMapping
    public ResponseEntity<Object> deleteHospital() {
        return hospitalService.deleteHospital();
    }
}
