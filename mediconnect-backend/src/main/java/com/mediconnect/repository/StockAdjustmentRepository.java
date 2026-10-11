package com.mediconnect.repository;

import com.mediconnect.entity.StockAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {

    /** Adjustments of one batch, with who made them. */
    @Query(value = """
            SELECT a FROM StockAdjustment a
            JOIN FETCH a.batch b
            LEFT JOIN FETCH a.adjustedBy u
            LEFT JOIN FETCH u.staff
            WHERE b.id = :batchId
            """,
            countQuery = "SELECT COUNT(a) FROM StockAdjustment a WHERE a.batch.id = :batchId")
    Page<StockAdjustment> findByBatchId(Long batchId, Pageable pageable);
}
