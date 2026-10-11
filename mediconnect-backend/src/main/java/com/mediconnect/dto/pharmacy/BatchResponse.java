package com.mediconnect.dto.pharmacy;

import com.mediconnect.entity.Medicine;
import com.mediconnect.entity.MedicineBatch;
import com.mediconnect.entity.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BatchResponse(
        Long id,
        Long medicineId,
        String medicineName,
        String form,
        String strength,
        String batchNumber,
        LocalDate expiryDate,
        boolean expired,
        int quantityReceived,
        int quantityOnHand,
        BigDecimal unitPrice,
        Instant receivedAt,
        String receivedByName
) {
    /** Expects medicine and receivedBy (+ staff) to be loaded. today = hospital date. */
    public static BatchResponse from(MedicineBatch b, LocalDate today) {
        Medicine m = b.getMedicine();
        User u = b.getReceivedBy();
        return new BatchResponse(b.getId(), m.getId(), m.getName(), m.getForm(), m.getStrength(),
                b.getBatchNumber(), b.getExpiryDate(), b.getExpiryDate().isBefore(today),
                b.getQuantityReceived(), b.getQuantityOnHand(), b.getUnitPrice(), b.getReceivedAt(),
                u != null && u.getStaff() != null ? u.getStaff().getFullName() : null);
    }
}
