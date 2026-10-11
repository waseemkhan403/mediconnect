package com.mediconnect.repository;

import com.mediconnect.entity.Medicine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface MedicineRepository extends JpaRepository<Medicine, Long> {

    /**
     * Friendly duplicate pre-check (name + form + strength, ignoring case); the unique index in schema.sql is the
     * real guard. excludeId: the row being updated (null on create).
     */
    @Query("""
            SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM Medicine m
            WHERE LOWER(m.name) = LOWER(:name)
              AND m.form = :form
              AND LOWER(COALESCE(m.strength, '')) = LOWER(COALESCE(CAST(:strength AS String), ''))
              AND (CAST(:excludeId AS Long) IS NULL OR m.id <> :excludeId)
            """)
    boolean existsDuplicate(String name, String form, String strength, Long excludeId);

    /**
     * Paged catalogue. Null = do not filter. name matches the name or the generic name.
     * lowStock: usable stock (non-expired batches) at or below the reorder level.
     */
    @Query("""
            SELECT m FROM Medicine m
            WHERE (CAST(:name AS String) IS NULL
                   OR LOWER(m.name) LIKE LOWER(CONCAT('%', CAST(:name AS String), '%'))
                   OR LOWER(m.genericName) LIKE LOWER(CONCAT('%', CAST(:name AS String), '%')))
              AND (CAST(:form AS String) IS NULL OR m.form = :form)
              AND (CAST(:active AS Boolean) IS NULL OR m.active = :active)
              AND (:lowStock = false OR
                   (SELECT COALESCE(SUM(b.quantityOnHand), 0) FROM MedicineBatch b
                    WHERE b.medicine = m AND b.expiryDate >= :today) <= m.reorderLevel)
            """)
    Page<Medicine> findAllWithFilters(String name, String form, Boolean active, boolean lowStock, LocalDate today,
                                      Pageable pageable);

    /** Usable stock per medicine (non-expired batches). Rows: [medicineId, quantity]; medicines without stock are absent. */
    @Query("""
            SELECT b.medicine.id, SUM(b.quantityOnHand) FROM MedicineBatch b
            WHERE b.medicine.id IN :medicineIds AND b.expiryDate >= :today
            GROUP BY b.medicine.id
            """)
    List<Object[]> sumUsableStock(Collection<Long> medicineIds, LocalDate today);
}
