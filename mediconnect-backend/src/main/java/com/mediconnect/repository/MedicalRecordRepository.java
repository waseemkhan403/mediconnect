package com.mediconnect.repository;

import com.mediconnect.entity.MedicalRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {

    /** Record with patient and uploader (+ staff, for the role) loaded. */
    @Query("""
            SELECT r FROM MedicalRecord r
            JOIN FETCH r.patient
            LEFT JOIN FETCH r.uploadedBy u
            LEFT JOIN FETCH u.staff
            WHERE r.id = :id
            """)
    Optional<MedicalRecord> findDetailById(Long id);

    /**
     * Paged list. Null = do not filter. Scoping (set by the service from the caller):
     * ownerId = patient login's own records, uploaderId = only what the caller uploaded.
     * Retracted records are hidden unless includeRetracted is true.
     */
    @Query(value = """
            SELECT r FROM MedicalRecord r
            JOIN FETCH r.patient p
            LEFT JOIN FETCH r.uploadedBy u
            LEFT JOIN FETCH u.staff
            LEFT JOIN p.owner o
            WHERE (CAST(:patientId AS Long) IS NULL OR p.id = :patientId)
              AND (CAST(:recordType AS String) IS NULL OR r.recordType = :recordType)
              AND (CAST(:ownerId AS Long) IS NULL OR o.id = :ownerId)
              AND (CAST(:uploaderId AS Long) IS NULL OR u.id = :uploaderId)
              AND (:includeRetracted = true OR r.status = 'ACTIVE')
            """,
            countQuery = """
            SELECT COUNT(r) FROM MedicalRecord r
            JOIN r.patient p
            LEFT JOIN r.uploadedBy u
            LEFT JOIN p.owner o
            WHERE (CAST(:patientId AS Long) IS NULL OR p.id = :patientId)
              AND (CAST(:recordType AS String) IS NULL OR r.recordType = :recordType)
              AND (CAST(:ownerId AS Long) IS NULL OR o.id = :ownerId)
              AND (CAST(:uploaderId AS Long) IS NULL OR u.id = :uploaderId)
              AND (:includeRetracted = true OR r.status = 'ACTIVE')
            """)
    Page<MedicalRecord> findAllWithFilters(
            Long patientId,
            String recordType,
            Long ownerId,
            Long uploaderId,
            boolean includeRetracted,
            Pageable pageable);
}
