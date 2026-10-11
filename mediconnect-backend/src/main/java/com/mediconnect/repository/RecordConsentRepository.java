package com.mediconnect.repository;

import com.mediconnect.entity.RecordConsent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RecordConsentRepository extends JpaRepository<RecordConsent, Long> {

    /** The check behind every consent-based read. */
    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
            FROM RecordConsent c
            WHERE c.patient.id = :patientId
              AND c.doctor.id = :doctorId
              AND c.revokedAt IS NULL
              AND c.expiresAt > :now
            """)
    boolean existsActive(Long patientId, Long doctorId, Instant now);

    /** Consent with patient and doctor (+ staff, for the name) loaded. */
    @Query("""
            SELECT c FROM RecordConsent c
            JOIN FETCH c.patient
            JOIN FETCH c.doctor d
            JOIN FETCH d.staff
            WHERE c.id = :id
            """)
    Optional<RecordConsent> findDetailById(Long id);

    /** Paged list; null = do not filter. activeOnly hides revoked and expired grants. */
    @Query(value = """
            SELECT c FROM RecordConsent c
            JOIN FETCH c.patient p
            JOIN FETCH c.doctor d
            JOIN FETCH d.staff
            LEFT JOIN p.owner o
            WHERE (CAST(:patientId AS Long) IS NULL OR p.id = :patientId)
              AND (CAST(:doctorId AS Long) IS NULL OR d.id = :doctorId)
              AND (CAST(:ownerId AS Long) IS NULL OR o.id = :ownerId)
              AND (:activeOnly = false OR (c.revokedAt IS NULL AND c.expiresAt > :now))
            """,
            countQuery = """
            SELECT COUNT(c) FROM RecordConsent c
            JOIN c.patient p
            LEFT JOIN p.owner o
            WHERE (CAST(:patientId AS Long) IS NULL OR p.id = :patientId)
              AND (CAST(:doctorId AS Long) IS NULL OR c.doctor.id = :doctorId)
              AND (CAST(:ownerId AS Long) IS NULL OR o.id = :ownerId)
              AND (:activeOnly = false OR (c.revokedAt IS NULL AND c.expiresAt > :now))
            """)
    Page<RecordConsent> findAllWithFilters(Long patientId, Long doctorId, Long ownerId, boolean activeOnly,
                                           Instant now, Pageable pageable);
}
