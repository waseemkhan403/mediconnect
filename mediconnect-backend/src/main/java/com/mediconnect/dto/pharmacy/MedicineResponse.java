package com.mediconnect.dto.pharmacy;

import com.mediconnect.entity.Medicine;

/** stockOnHand: units in batches that have not expired. lowStock: stockOnHand at or below reorderLevel. */
public record MedicineResponse(
        Long id,
        String name,
        String genericName,
        String form,
        String strength,
        String manufacturer,
        int reorderLevel,
        boolean active,
        long stockOnHand,
        boolean lowStock
) {
    public static MedicineResponse from(Medicine m, long stockOnHand) {
        return new MedicineResponse(m.getId(), m.getName(), m.getGenericName(), m.getForm(), m.getStrength(),
                m.getManufacturer(), m.getReorderLevel(), m.isActive(), stockOnHand,
                stockOnHand <= m.getReorderLevel());
    }
}
