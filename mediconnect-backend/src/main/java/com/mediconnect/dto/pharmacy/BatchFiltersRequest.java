package com.mediconnect.dto.pharmacy;

/**
 * All fields optional. expiringWithinDays: batches expiring within that many days, already expired ones included
 * (0 = expired or expiring today). inStockOnly (default false): hide empty batches.
 */
public record BatchFiltersRequest(Long medicineId, Integer expiringWithinDays, Boolean inStockOnly) {
}
