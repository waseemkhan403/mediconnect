package com.mediconnect.controller;

import com.mediconnect.dto.staff.StaffFiltersRequest;
import com.mediconnect.dto.staff.StaffRequest;
import com.mediconnect.helper.PageableBuilder;
import com.mediconnect.security.Access;
import com.mediconnect.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Non-doctor staff. Doctors are managed through /doctors. */
@RestController
@RequestMapping("/staff")
@RequiredArgsConstructor
@PreAuthorize(Access.ADMIN)
public class StaffController {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "id", "id",
            "staffCode", "staffCode",
            "fullName", "fullName",
            "staffType", "staffType",
            "status", "status",
            "joiningDate", "joiningDate");

    private final StaffService staffService;
    private final PageableBuilder pageableBuilder;

    /**
     * POST /staff/list?page=0&size=10&sort=fullName,asc
     * Body (optional): { "name": "ravi", "staffType": "NURSE", "status": "ACTIVE", "departmentId": 3 }
     */
    @PostMapping("/list")
    public ResponseEntity<Object> getStaffList(
            @RequestBody(required = false) StaffFiltersRequest filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String[] sort) {
        return staffService.getStaffList(filters, pageableBuilder.build(page, size, sort, SORT_FIELDS));
    }

    /** GET /staff/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Object> getStaffById(@PathVariable Long id) {
        return staffService.getStaffById(id);
    }

    /** POST /staff */
    @PostMapping
    public ResponseEntity<Object> addStaff(@Valid @RequestBody StaffRequest request) {
        return staffService.addStaff(request);
    }

    /** PUT /staff/{id} */
    @PutMapping("/{id}")
    public ResponseEntity<Object> updateStaff(
            @PathVariable Long id,
            @Valid @RequestBody StaffRequest request) {
        return staffService.updateStaff(id, request);
    }

    /** DELETE /staff/{id} : for mistaken entries; prefer setting status to INACTIVE. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteStaff(@PathVariable Long id) {
        return staffService.deleteStaff(id);
    }
}
