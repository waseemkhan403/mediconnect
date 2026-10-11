package com.mediconnect.dto.pharmacy;

/**
 * All fields optional. name matches the name or the generic name (contains, any case).
 * lowStock = true: only medicines whose usable stock is at or below their reorder level.
 */
public record MedicineFiltersRequest(String name, String form, Boolean active, Boolean lowStock) {
}
