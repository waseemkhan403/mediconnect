package com.mediconnect.repository;

import com.mediconnect.entity.StaffLeave;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface StaffLeaveRepository extends JpaRepository<StaffLeave, Long> {

    /** Leave with the staff row loaded (name, type for responses). */
    @Query("""
            SELECT l FROM StaffLeave l
            JOIN FETCH l.staff
            WHERE l.id = :id
            """)
    Optional<StaffLeave> findDetailById(Long id);

    /**
     * Another PENDING or APPROVED leave of the same person overlapping [startDate, endDate].
     * Pass excludeId = -1 when creating.
     */
    @Query("""
            SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END
            FROM StaffLeave l
            WHERE l.staff.id = :staffId
              AND l.status IN ('PENDING', 'APPROVED')
              AND l.startDate <= :endDate
              AND l.endDate >= :startDate
              AND l.id <> :excludeId
            """)
    boolean existsOverlap(Long staffId, LocalDate startDate, LocalDate endDate, Long excludeId);

    /** Is this person on approved leave on this date? (slot generation and booking) */
    @Query("""
            SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END
            FROM StaffLeave l
            WHERE l.staff.id = :staffId
              AND l.status = 'APPROVED'
              AND l.startDate <= :date
              AND l.endDate >= :date
            """)
    boolean isOnApprovedLeave(Long staffId, LocalDate date);

    /**
     * Paged list. staffId/status null = do not filter. from/to are always given (the service substitutes wide
     * bounds); a leave matches when it overlaps [from, to].
     */
    @Query(value = """
            SELECT l FROM StaffLeave l
            JOIN FETCH l.staff s
            WHERE (CAST(:staffId AS Long) IS NULL OR s.id = :staffId)
              AND (CAST(:status AS String) IS NULL OR l.status = :status)
              AND l.endDate >= :from
              AND l.startDate <= :to
            """,
            countQuery = """
            SELECT COUNT(l) FROM StaffLeave l
            WHERE (CAST(:staffId AS Long) IS NULL OR l.staff.id = :staffId)
              AND (CAST(:status AS String) IS NULL OR l.status = :status)
              AND l.endDate >= :from
              AND l.startDate <= :to
            """)
    Page<StaffLeave> findAllWithFilters(Long staffId, String status, LocalDate from, LocalDate to, Pageable pageable);
}
