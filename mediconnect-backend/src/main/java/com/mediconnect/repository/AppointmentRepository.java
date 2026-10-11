package com.mediconnect.repository;

import com.mediconnect.entity.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * "Active" below means the booking still occupies its slot: every status except CANCELLED and RESCHEDULED
 * (the same rule as the partial unique index in schema.sql).
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /** Appointment with patient and doctor (+ staff, for the name) loaded, for building responses. */
    @Query("""
            SELECT a FROM Appointment a
            JOIN FETCH a.patient
            JOIN FETCH a.doctor d
            JOIN FETCH d.staff
            WHERE a.id = :id
            """)
    Optional<Appointment> findDetailById(Long id);

    /** Active bookings of a doctor starting inside [from, to): used to hide taken slots. */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.doctor.id = :doctorId
              AND a.slotStart >= :from
              AND a.slotStart < :to
              AND a.status NOT IN ('CANCELLED', 'RESCHEDULED')
            """)
    List<Appointment> findActiveForDoctor(Long doctorId, Instant from, Instant to);

    /**
     * True if the doctor already has an active booking overlapping [start, end). Overlap rather than equal start,
     * because changing a schedule's slot length can shift the slot grid under existing bookings.
     */
    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM Appointment a
            WHERE a.doctor.id = :doctorId
              AND a.slotStart < :end
              AND a.slotEnd > :start
              AND a.status NOT IN ('CANCELLED', 'RESCHEDULED')
            """)
    boolean existsDoctorOverlap(Long doctorId, Instant start, Instant end);

    /** Same check for the patient (with any doctor): one person cannot be in two consultations at once. */
    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM Appointment a
            WHERE a.patient.id = :patientId
              AND a.slotStart < :end
              AND a.slotEnd > :start
              AND a.status NOT IN ('CANCELLED', 'RESCHEDULED')
            """)
    boolean existsPatientOverlap(Long patientId, Instant start, Instant end);

    /**
     * Bookings still waiting to happen (REQUESTED / CONFIRMED) for a doctor inside [from, to), with names loaded.
     * Used to show reception which bookings a doctor's leave affects.
     */
    @Query("""
            SELECT a FROM Appointment a
            JOIN FETCH a.patient
            JOIN FETCH a.doctor d
            JOIN FETCH d.staff
            WHERE d.id = :doctorId
              AND a.slotStart >= :from
              AND a.slotStart < :to
              AND a.status IN ('REQUESTED', 'CONFIRMED')
            ORDER BY a.slotStart
            """)
    List<Appointment> findUpcomingForDoctor(Long doctorId, Instant from, Instant to);

    /** Does this doctor treat (have any booking with) this patient? Doctors may only open such patients. */
    boolean existsByDoctor_IdAndPatient_Id(Long doctorId, Long patientId);

    /**
     * Paged list. Pass null to skip the id/status filters. from/to are always given (the service
     * substitutes wide bounds), so no CAST is needed on the instants.
     * ownerId restricts a patient login to bookings of the patient records it owns.
     */
    @Query(value = """
            SELECT a FROM Appointment a
            JOIN FETCH a.patient p
            JOIN FETCH a.doctor d
            JOIN FETCH d.staff
            LEFT JOIN p.owner o
            WHERE (CAST(:doctorId AS Long) IS NULL OR d.id = :doctorId)
              AND (CAST(:patientId AS Long) IS NULL OR p.id = :patientId)
              AND (CAST(:ownerId AS Long) IS NULL OR o.id = :ownerId)
              AND (CAST(:status AS String) IS NULL OR a.status = :status)
              AND a.slotStart >= :from
              AND a.slotStart < :to
            """,
            countQuery = """
            SELECT COUNT(a) FROM Appointment a
            JOIN a.patient p
            LEFT JOIN p.owner o
            WHERE (CAST(:doctorId AS Long) IS NULL OR a.doctor.id = :doctorId)
              AND (CAST(:patientId AS Long) IS NULL OR p.id = :patientId)
              AND (CAST(:ownerId AS Long) IS NULL OR o.id = :ownerId)
              AND (CAST(:status AS String) IS NULL OR a.status = :status)
              AND a.slotStart >= :from
              AND a.slotStart < :to
            """)
    Page<Appointment> findAllWithFilters(
            Long doctorId,
            Long patientId,
            Long ownerId,
            String status,
            Instant from,
            Instant to,
            Pageable pageable);
}
