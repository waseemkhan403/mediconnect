package com.mediconnect.repository;

import com.mediconnect.entity.ChatThread;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface ChatThreadRepository extends JpaRepository<ChatThread, Long> {

    boolean existsByEncounter_Id(Long encounterId);

    /** Thread with encounter, appointment, patient (+ owner) and doctor (+ staff) loaded. */
    @Query("""
            SELECT t FROM ChatThread t
            JOIN FETCH t.encounter e
            JOIN FETCH e.appointment a
            JOIN FETCH a.patient p
            LEFT JOIN FETCH p.owner
            JOIN FETCH a.doctor d
            JOIN FETCH d.staff
            WHERE t.id = :id
            """)
    Optional<ChatThread> findDetailById(Long id);

    /**
     * Inbox. Null = do not filter. doctorId / ownerId are forced by the service to the caller's own threads.
     * openOnly hides threads that are closed or past their window.
     */
    @Query(value = """
            SELECT t FROM ChatThread t
            JOIN FETCH t.encounter e
            JOIN FETCH e.appointment a
            JOIN FETCH a.patient p
            LEFT JOIN FETCH p.owner o
            JOIN FETCH a.doctor d
            JOIN FETCH d.staff
            WHERE (CAST(:encounterId AS Long) IS NULL OR e.id = :encounterId)
              AND (CAST(:patientId AS Long) IS NULL OR p.id = :patientId)
              AND (CAST(:doctorId AS Long) IS NULL OR d.id = :doctorId)
              AND (CAST(:ownerId AS Long) IS NULL OR o.id = :ownerId)
              AND (:openOnly = false OR (t.closedAt IS NULL AND t.closesAt > :now))
            """,
            countQuery = """
            SELECT COUNT(t) FROM ChatThread t
            JOIN t.encounter e
            JOIN e.appointment a
            JOIN a.patient p
            LEFT JOIN p.owner o
            WHERE (CAST(:encounterId AS Long) IS NULL OR e.id = :encounterId)
              AND (CAST(:patientId AS Long) IS NULL OR p.id = :patientId)
              AND (CAST(:doctorId AS Long) IS NULL OR a.doctor.id = :doctorId)
              AND (CAST(:ownerId AS Long) IS NULL OR o.id = :ownerId)
              AND (:openOnly = false OR (t.closedAt IS NULL AND t.closesAt > :now))
            """)
    Page<ChatThread> findAllWithFilters(Long encounterId, Long patientId, Long doctorId, Long ownerId,
                                        boolean openOnly, Instant now, Pageable pageable);
}
