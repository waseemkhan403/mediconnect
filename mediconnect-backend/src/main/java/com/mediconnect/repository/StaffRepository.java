package com.mediconnect.repository;

import com.mediconnect.entity.Staff;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Long> {

    Optional<Staff> findByStaffCode(String staffCode);

    boolean existsByStaffCode(String staffCode);

    boolean existsByEmailIgnoreCase(String email);

    Optional<Staff> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /** Used to refuse deleting a department that still has staff (clearer than a raw FK error). */
    boolean existsByDepartmentId(Long departmentId);

    /**
     * Staff.department is LAZY and open-in-view is off, so fetch it explicitly
     * whenever the caller needs the department name.
     */
    @Query("""
            SELECT s FROM Staff s
            LEFT JOIN FETCH s.department
            WHERE s.id = :id
            """)
    Optional<Staff> findDetailById(Long id);

    @Query(value = """
            SELECT s FROM Staff s
            LEFT JOIN FETCH s.department d
            WHERE (CAST(:name AS String) IS NULL
                   OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', CAST(:name AS String), '%')))
              AND (CAST(:staffType AS String) IS NULL OR s.staffType = :staffType)
              AND (CAST(:status AS String) IS NULL OR s.status = :status)
              AND (CAST(:departmentId AS Long) IS NULL OR d.id = :departmentId)
            """,
            countQuery = """
            SELECT COUNT(s) FROM Staff s
            LEFT JOIN s.department d
            WHERE (CAST(:name AS String) IS NULL
                   OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', CAST(:name AS String), '%')))
              AND (CAST(:staffType AS String) IS NULL OR s.staffType = :staffType)
              AND (CAST(:status AS String) IS NULL OR s.status = :status)
              AND (CAST(:departmentId AS Long) IS NULL OR d.id = :departmentId)
            """)
    Page<Staff> findAllWithFilters(
            String name,
            String staffType,
            String status,
            Long departmentId,
            Pageable pageable);
}
