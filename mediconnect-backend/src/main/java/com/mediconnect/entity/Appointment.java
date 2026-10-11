package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A booking of one slot with one doctor. Only booked slots are stored; free slots are computed from
 * the doctor's weekly schedule. Times are stored as UTC instants (timestamptz) and shown in the hospital timezone.
 * status: REQUESTED | CONFIRMED | CHECKED_IN | IN_CONSULTATION | COMPLETED | CANCELLED | NO_SHOW | RESCHEDULED.
 * Double booking is blocked by a partial unique index on (doctor_id, slot_start) in schema.sql that ignores
 * CANCELLED and RESCHEDULED rows, so a freed slot can be booked again.
 * What happened during the visit (notes, prescriptions) will live on a separate Encounter, not here.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "appointments", check = {
        @CheckConstraint(name = "ck_appointments_status", constraint = "status IN ('REQUESTED', 'CONFIRMED', "
                + "'CHECKED_IN', 'IN_CONSULTATION', 'COMPLETED', 'CANCELLED', 'NO_SHOW', 'RESCHEDULED')"),
        @CheckConstraint(name = "ck_appointments_times", constraint = "slot_end > slot_start")
})
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appointment_id")
    private Long id;

    /** No ON DELETE: a patient or doctor with appointments cannot be deleted (deactivate them instead). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "slot_start", nullable = false)
    private Instant slotStart;

    @Column(name = "slot_end", nullable = false)
    private Instant slotEnd;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** Why the patient is coming, in their words. */
    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    /** Set on the new booking when it replaces an earlier one (the earlier one becomes RESCHEDULED). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rescheduled_from_id")
    private Appointment rescheduledFrom;
}
