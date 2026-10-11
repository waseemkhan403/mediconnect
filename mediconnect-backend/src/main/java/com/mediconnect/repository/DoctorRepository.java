package com.mediconnect.repository;

import com.mediconnect.entity.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    boolean existsByStaffId(Long staffId);

    Optional<Doctor> findByStaffId(Long staffId);

    /** Registration is unique per (council, number). */
    boolean existsByRegistrationCouncilAndRegistrationNumber(String council, String number);

    boolean existsByRegistrationCouncilAndRegistrationNumberAndIdNot(String council, String number, Long id);

    /** Doctor with staff and department loaded (name, contact, department live on Staff). */
    @Query("""
            SELECT d FROM Doctor d
            JOIN FETCH d.staff s
            LEFT JOIN FETCH s.department
            WHERE d.id = :id
            """)
    Optional<Doctor> findDetailById(Long id);

    /**
     * Paged doctor search. Pass null to skip a filter.
     * Language filtering (text[] column) is intentionally not here; it needs a native query.
     */
    @Query(value = """
            SELECT d FROM Doctor d
            JOIN FETCH d.staff s
            LEFT JOIN FETCH s.department dep
            WHERE (CAST(:name AS String) IS NULL
                   OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', CAST(:name AS String), '%')))
              AND (CAST(:specialization AS String) IS NULL
                   OR LOWER(d.specialization) LIKE LOWER(CONCAT('%', CAST(:specialization AS String), '%')))
              AND (CAST(:departmentId AS Long) IS NULL OR dep.id = :departmentId)
              AND (CAST(:verified AS Boolean) IS NULL OR d.verified = :verified)
              AND (CAST(:status AS String) IS NULL OR s.status = :status)
            """,
            countQuery = """
            SELECT COUNT(d) FROM Doctor d
            JOIN d.staff s
            LEFT JOIN s.department dep
            WHERE (CAST(:name AS String) IS NULL
                   OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', CAST(:name AS String), '%')))
              AND (CAST(:specialization AS String) IS NULL
                   OR LOWER(d.specialization) LIKE LOWER(CONCAT('%', CAST(:specialization AS String), '%')))
              AND (CAST(:departmentId AS Long) IS NULL OR dep.id = :departmentId)
              AND (CAST(:verified AS Boolean) IS NULL OR d.verified = :verified)
              AND (CAST(:status AS String) IS NULL OR s.status = :status)
            """)
    Page<Doctor> findAllWithFilters(
            String name,
            String specialization,
            Long departmentId,
            Boolean verified,
            String status,
            Pageable pageable);
}
