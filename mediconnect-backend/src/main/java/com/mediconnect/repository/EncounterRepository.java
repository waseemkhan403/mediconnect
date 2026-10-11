package com.mediconnect.repository;

import com.mediconnect.entity.Encounter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface EncounterRepository extends JpaRepository<Encounter, Long> {

    boolean existsByAppointment_Id(Long appointmentId);

    /**
     * Encounter with appointment, patient and doctor (+ staff) loaded. The two collections (prescription, addenda)
     * stay lazy and load inside the service transaction: fetching two lists in one query is not allowed.
     */
    @Query("""
            SELECT e FROM Encounter e
            JOIN FETCH e.appointment a
            JOIN FETCH a.patient
            JOIN FETCH a.doctor d
            JOIN FETCH d.staff
            WHERE e.id = :id
            """)
    Optional<Encounter> findDetailById(Long id);

    /**
     * Paged list. Null = do not filter. from/to always given (wide bounds from the service).
     * ownerId restricts a patient login to its own records.
     * viewerDoctorId (a doctor reading a consenting patient's history): FINAL encounters of any doctor plus
     * that doctor's own drafts, never other doctors' drafts.
     */
    @Query(value = """
            SELECT e FROM Encounter e
            JOIN FETCH e.appointment a
            JOIN FETCH a.patient p
            JOIN FETCH a.doctor d
            JOIN FETCH d.staff
            LEFT JOIN p.owner o
            WHERE (CAST(:appointmentId AS Long) IS NULL OR a.id = :appointmentId)
              AND (CAST(:patientId AS Long) IS NULL OR p.id = :patientId)
              AND (CAST(:doctorId AS Long) IS NULL OR d.id = :doctorId)
              AND (CAST(:ownerId AS Long) IS NULL OR o.id = :ownerId)
              AND (CAST(:viewerDoctorId AS Long) IS NULL OR e.status = 'FINAL' OR d.id = :viewerDoctorId)
              AND (CAST(:status AS String) IS NULL OR e.status = :status)
              AND a.slotStart >= :from
              AND a.slotStart < :to
            """,
            countQuery = """
            SELECT COUNT(e) FROM Encounter e
            JOIN e.appointment a
            JOIN a.patient p
            LEFT JOIN p.owner o
            WHERE (CAST(:appointmentId AS Long) IS NULL OR a.id = :appointmentId)
              AND (CAST(:patientId AS Long) IS NULL OR p.id = :patientId)
              AND (CAST(:doctorId AS Long) IS NULL OR a.doctor.id = :doctorId)
              AND (CAST(:ownerId AS Long) IS NULL OR o.id = :ownerId)
              AND (CAST(:viewerDoctorId AS Long) IS NULL OR e.status = 'FINAL' OR a.doctor.id = :viewerDoctorId)
              AND (CAST(:status AS String) IS NULL OR e.status = :status)
              AND a.slotStart >= :from
              AND a.slotStart < :to
            """)
    Page<Encounter> findAllWithFilters(
            Long appointmentId,
            Long patientId,
            Long doctorId,
            Long ownerId,
            Long viewerDoctorId,
            String status,
            Instant from,
            Instant to,
            Pageable pageable);

    /**
     * Pharmacy queue: FINAL encounters that have a prescription. pendingOnly hides those already dispensed
     * (at least once). Null patientId = all patients.
     */
    @Query(value = """
            SELECT e FROM Encounter e
            JOIN FETCH e.appointment a
            JOIN FETCH a.patient p
            JOIN FETCH a.doctor d
            JOIN FETCH d.staff
            WHERE e.status = 'FINAL'
              AND EXISTS (SELECT 1 FROM PrescriptionItem i WHERE i.encounter = e)
              AND (CAST(:patientId AS Long) IS NULL OR p.id = :patientId)
              AND (:pendingOnly = false OR NOT EXISTS (SELECT 1 FROM Dispensation x WHERE x.encounter = e))
            """,
            countQuery = """
            SELECT COUNT(e) FROM Encounter e
            JOIN e.appointment a
            WHERE e.status = 'FINAL'
              AND EXISTS (SELECT 1 FROM PrescriptionItem i WHERE i.encounter = e)
              AND (CAST(:patientId AS Long) IS NULL OR a.patient.id = :patientId)
              AND (:pendingOnly = false OR NOT EXISTS (SELECT 1 FROM Dispensation x WHERE x.encounter = e))
            """)
    Page<Encounter> findPrescriptionQueue(Long patientId, boolean pendingOnly, Pageable pageable);
}
