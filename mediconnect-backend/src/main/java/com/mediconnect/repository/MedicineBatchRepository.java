package com.mediconnect.repository;

import com.mediconnect.entity.MedicineBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MedicineBatchRepository extends JpaRepository<MedicineBatch, Long> {

    boolean existsByMedicine_IdAndBatchNumberIgnoreCaseAndIdNot(Long medicineId, String batchNumber, Long id);

    boolean existsByMedicine_IdAndBatchNumberIgnoreCase(Long medicineId, String batchNumber);

    boolean existsByMedicine_Id(Long medicineId);

    /** Batch with medicine and receiver (+ staff) loaded. */
    @Query("""
            SELECT b FROM MedicineBatch b
            JOIN FETCH b.medicine
            LEFT JOIN FETCH b.receivedBy u
            LEFT JOIN FETCH u.staff
            WHERE b.id = :id
            """)
    Optional<MedicineBatch> findDetailById(Long id);

    /**
     * The batch row locked (SELECT ... FOR UPDATE) for a stock adjustment, so a dispense running at the same time
     * waits instead of both reading the same quantity.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM MedicineBatch b WHERE b.id = :id")
    Optional<MedicineBatch> findByIdForUpdate(Long id);

    /**
     * Batches a medicine can be dispensed from, earliest expiry first (FEFO: first expiry, first out, so stock is
     * used before it expires), locked until the transaction ends.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM MedicineBatch b
            WHERE b.medicine.id = :medicineId
              AND b.expiryDate >= :today
              AND b.quantityOnHand > 0
            ORDER BY b.expiryDate ASC, b.id ASC
            """)
    List<MedicineBatch> findDispensableForUpdate(Long medicineId, LocalDate today);

    /** Paged list. Null = do not filter. expiringBy: expiry on or before this date (expired ones included). */
    @Query(value = """
            SELECT b FROM MedicineBatch b
            JOIN FETCH b.medicine m
            LEFT JOIN FETCH b.receivedBy u
            LEFT JOIN FETCH u.staff
            WHERE (CAST(:medicineId AS Long) IS NULL OR m.id = :medicineId)
              AND (CAST(:expiringBy AS LocalDate) IS NULL OR b.expiryDate <= :expiringBy)
              AND (:inStockOnly = false OR b.quantityOnHand > 0)
            """,
            countQuery = """
            SELECT COUNT(b) FROM MedicineBatch b
            WHERE (CAST(:medicineId AS Long) IS NULL OR b.medicine.id = :medicineId)
              AND (CAST(:expiringBy AS LocalDate) IS NULL OR b.expiryDate <= :expiringBy)
              AND (:inStockOnly = false OR b.quantityOnHand > 0)
            """)
    Page<MedicineBatch> findAllWithFilters(Long medicineId, LocalDate expiringBy, boolean inStockOnly,
                                           Pageable pageable);
}
