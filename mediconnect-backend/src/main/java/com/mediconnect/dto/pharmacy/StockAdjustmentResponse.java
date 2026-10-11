package com.mediconnect.dto.pharmacy;

import com.mediconnect.entity.StockAdjustment;
import com.mediconnect.entity.User;

import java.time.Instant;

public record StockAdjustmentResponse(
        Long id,
        Long batchId,
        int quantityChange,
        int quantityAfter,
        String reason,
        String note,
        String adjustedByName,
        Instant adjustedAt
) {
    /** Expects adjustedBy (+ staff) to be loaded. */
    public static StockAdjustmentResponse from(StockAdjustment a) {
        User u = a.getAdjustedBy();
        return new StockAdjustmentResponse(a.getId(), a.getBatch().getId(), a.getQuantityChange(),
                a.getQuantityAfter(), a.getReason(), a.getNote(),
                u != null && u.getStaff() != null ? u.getStaff().getFullName() : null, a.getAdjustedAt());
    }
}
