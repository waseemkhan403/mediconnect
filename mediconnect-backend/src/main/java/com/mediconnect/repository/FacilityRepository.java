package com.mediconnect.repository;

import com.mediconnect.entity.Facility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FacilityRepository extends JpaRepository<Facility, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /** Active facilities for the public hospital page. */
    List<Facility> findByActiveTrueOrderByNameAsc();

    @Query("""
            SELECT f FROM Facility f
            WHERE (CAST(:name AS String) IS NULL
                   OR LOWER(f.name) LIKE LOWER(CONCAT('%', CAST(:name AS String), '%')))
              AND (CAST(:active AS Boolean) IS NULL OR f.active = :active)
            """)
    Page<Facility> findAllWithFilters(String name, Boolean active, Pageable pageable);
}
