package com.mediconnect.repository;

import com.mediconnect.entity.Dispensation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DispensationRepository extends JpaRepository<Dispensation, Long> {

    /** Dispensation with encounter, appointment, patient (+ owner), doctor (+ staff) and dispenser loaded. */
    @Query("""
            SELECT x FROM Dispensation x
            JOIN FETCH x.encounter e
            JOIN FETCH e.appointment a
            JOIN FETCH a.patient p
            LEFT JOIN FETCH p.owner
            JOIN FETCH a.doctor d
            JOIN FETCH d.staff
            LEFT JOIN FETCH x.dispensedBy u
            LEFT JOIN FETCH u.staff
            WHERE x.id = :id
            """)
    Optional<Dispensation> findDetailById(Long id);

    /** Which of these encounters already have at least one dispensation (prescription queue). */
    @Query("SELECT DISTINCT x.encounter.id FROM Dispensation x WHERE x.encounter.id IN :encounterIds")
    List<Long> findDispensedEncounterIds(Collection<Long> encounterIds);

    /**
     * Paged list. Null = do not filter. doctorId / ownerId are forced by the service for doctors and patients.
     * from/to always given (wide bounds from the service).
     */
    @Query(value = """
            SELECT x FROM Dispensation x
            JOIN FETCH x.encounter e
            JOIN FETCH e.appointment a
            JOIN FETCH a.patient p
            JOIN FETCH a.doctor d
            JOIN FETCH d.staff
            LEFT JOIN FETCH x.dispensedBy u
            LEFT JOIN FETCH u.staff
            LEFT JOIN p.owner o
            WHERE (CAST(:encounterId AS Long) IS NULL OR e.id = :encounterId)
              AND (CAST(:patientId AS Long) IS NULL OR p.id = :patientId)
              AND (CAST(:doctorId AS Long) IS NULL OR d.id = :doctorId)
              AND (CAST(:ownerId AS Long) IS NULL OR o.id = :ownerId)
              AND x.dispensedAt >= :from
              AND x.dispensedAt < :to
            """,
            countQuery = """
            SELECT COUNT(x) FROM Dispensation x
            JOIN x.encounter e
            JOIN e.appointment a
            JOIN a.patient p
            LEFT JOIN p.owner o
            WHERE (CAST(:encounterId AS Long) IS NULL OR e.id = :encounterId)
              AND (CAST(:patientId AS Long) IS NULL OR p.id = :patientId)
              AND (CAST(:doctorId AS Long) IS NULL OR a.doctor.id = :doctorId)
              AND (CAST(:ownerId AS Long) IS NULL OR o.id = :ownerId)
              AND x.dispensedAt >= :from
              AND x.dispensedAt < :to
            """)
    Page<Dispensation> findAllWithFilters(Long encounterId, Long patientId, Long doctorId, Long ownerId,
                                          Instant from, Instant to, Pageable pageable);
}
