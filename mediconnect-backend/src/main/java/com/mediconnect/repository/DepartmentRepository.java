package com.mediconnect.repository;

import com.mediconnect.entity.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    /** Friendly pre-check; the unique index on lower(name) is the real guard against races. */
    boolean existsByNameIgnoreCase(String name);

    /** Same check for updates: ignore the row being updated. */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /** Active departments for dropdowns. */
    List<Department> findByActiveTrueOrderByNameAsc();

    /**
     * Paged list with optional filters (pass null to skip a filter).
     * CAST(... AS ...) gives PostgreSQL a concrete type when the parameter is null.
     */
    @Query("""
            SELECT d FROM Department d
            WHERE (CAST(:name AS String) IS NULL
                   OR LOWER(d.name) LIKE LOWER(CONCAT('%', CAST(:name AS String), '%')))
              AND (CAST(:active AS Boolean) IS NULL OR d.active = :active)
            """)
    Page<Department> findAllWithFilters(String name, Boolean active, Pageable pageable);
}
