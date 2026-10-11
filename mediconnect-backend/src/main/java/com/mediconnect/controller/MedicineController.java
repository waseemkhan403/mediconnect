package com.mediconnect.controller;

import com.mediconnect.dto.pharmacy.MedicineFiltersRequest;
import com.mediconnect.dto.pharmacy.MedicineRequest;
import com.mediconnect.helper.PageableBuilder;
import com.mediconnect.security.Access;
import com.mediconnect.service.MedicineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Pharmacy catalogue. Any staff member can read it (doctors pick from it); pharmacist and admin write it. */
@RestController
@RequestMapping("/medicines")
@RequiredArgsConstructor
@PreAuthorize(Access.STAFF)
public class MedicineController {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "id", "id",
            "name", "name",
            "form", "form",
            "reorderLevel", "reorderLevel");

    private final MedicineService medicineService;
    private final PageableBuilder pageableBuilder;

    /**
     * POST /medicines/list?page=0&size=10&sort=name,asc
     * Body (optional): { "name": "para", "form": "TABLET", "active": true, "lowStock": true }
     */
    @PostMapping("/list")
    public ResponseEntity<Object> getMedicines(
            @RequestBody(required = false) MedicineFiltersRequest filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name,asc") String[] sort) {
        return medicineService.getMedicines(filters, pageableBuilder.build(page, size, sort, SORT_FIELDS));
    }

    /**
     * POST /medicines
     * Body: { "name": "Dolo 650", "genericName": "Paracetamol", "form": "TABLET", "strength": "650 mg",
     *         "manufacturer": "Micro Labs", "reorderLevel": 100 }
     */
    @PreAuthorize(Access.PHARMACY)
    @PostMapping
    public ResponseEntity<Object> addMedicine(@Valid @RequestBody MedicineRequest request) {
        return medicineService.addMedicine(request);
    }

    /** GET /medicines/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Object> getMedicineById(@PathVariable Long id) {
        return medicineService.getMedicineById(id);
    }

    /** PUT /medicines/{id}   Body: same as POST (replaces the entry; "active": false to stop using it). */
    @PreAuthorize(Access.PHARMACY)
    @PutMapping("/{id}")
    public ResponseEntity<Object> updateMedicine(@PathVariable Long id, @Valid @RequestBody MedicineRequest request) {
        return medicineService.updateMedicine(id, request);
    }

    /** DELETE /medicines/{id} : only if never stocked or prescribed. */
    @PreAuthorize(Access.PHARMACY)
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteMedicine(@PathVariable Long id) {
        return medicineService.deleteMedicine(id);
    }
}
