package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Weekly recurring template: "Mondays 09:00-13:00, 15-minute slots, in person".
 * Times are LOCAL to the hospital timezone. Slots are generated from this at read time, not stored.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "doctor_schedules", check = {
        @CheckConstraint(name = "ck_schedules_times", constraint = "end_time > start_time"),
        @CheckConstraint(name = "ck_schedules_slot", constraint = "slot_duration_minutes > 0"),
        @CheckConstraint(name = "ck_schedules_validity", constraint = "valid_to IS NULL OR valid_to >= valid_from")
})
public class DoctorSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long id;

    /** ON DELETE CASCADE: schedules go with their doctor (DoctorService.deleteDoctor relies on this). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Doctor doctor;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_duration_minutes", nullable = false)
    private Integer slotDurationMinutes;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    /** Null = open-ended. */
    @Column(name = "valid_to")
    private LocalDate validTo;
}
