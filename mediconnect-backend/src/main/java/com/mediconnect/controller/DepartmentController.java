package com.mediconnect.controller;

import com.mediconnect.dto.department.DepartmentFiltersRequest;
import com.mediconnect.dto.department.DepartmentRequest;
import com.mediconnect.helper.PageableBuilder;
import com.mediconnect.security.Access;
import com.mediconnect.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
@PreAuthorize(Access.STAFF)
public class DepartmentController {

    /** API sort name -> entity property. */
    private static final Map<String, String> SORT_FIELDS =
            Map.of("id", "id", "name", "name", "active", "active");

    private final DepartmentService departmentService;
    private final PageableBuilder pageableBuilder;

    /**
     * POST /departments/list?page=0&size=10&sort=name,asc
     * Body (optional): { "name": "card", "active": true }
     */
    @PostMapping("/list")
    public ResponseEntity<Object> getDepartments(
            @RequestBody(required = false) DepartmentFiltersRequest filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String[] sort) {
        return departmentService.getDepartments(filters, pageableBuilder.build(page, size, sort, SORT_FIELDS));
    }

    /** GET /departments/active : unpaged, for dropdowns. */
    @GetMapping("/active")
    public ResponseEntity<Object> getActiveDepartments() {
        return departmentService.getActiveDepartments();
    }

    /** GET /departments/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Object> getDepartmentById(@PathVariable Long id) {
        return departmentService.getDepartmentById(id);
    }

    /** POST /departments */
    @PreAuthorize(Access.ADMIN)
    @PostMapping
    public ResponseEntity<Object> addDepartment(@Valid @RequestBody DepartmentRequest request) {
        return departmentService.addDepartment(request);
    }

    /** PUT /departments/{id} */
    @PreAuthorize(Access.ADMIN)
    @PutMapping("/{id}")
    public ResponseEntity<Object> updateDepartment(
            @PathVariable Long id,
            @Valid @RequestBody DepartmentRequest request) {
        return departmentService.updateDepartment(id, request);
    }

    /** DELETE /departments/{id} : refused while staff are assigned. */
    @PreAuthorize(Access.ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteDepartment(@PathVariable Long id) {
        return departmentService.deleteDepartment(id);
    }
}
