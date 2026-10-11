package com.mediconnect.repository;

import com.mediconnect.entity.DoctorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, Long> {

    /** open-ended schedules (valid_to = null) are treated as ending on this date when checking overlaps. */
    LocalDate FAR_FUTURE = LocalDate.of(9999, 12, 31);

    /**
     * All schedules of a doctor. day_of_week is stored as text, so sorting by it in SQL would be
     * alphabetical (FRIDAY first); sort by DayOfWeek in the service instead.
     */
    List<DoctorSchedule> findByDoctorId(Long doctorId);

    /** Schedules in force on a given date for a given weekday. Slot generation starts from this. */
    @Query("""
            SELECT s FROM DoctorSchedule s
            WHERE s.doctor.id = :doctorId
              AND s.dayOfWeek = :dayOfWeek
              AND s.validFrom <= :date
              AND (s.validTo IS NULL OR s.validTo >= :date)
            ORDER BY s.startTime
            """)
    List<DoctorSchedule> findEffective(Long doctorId, DayOfWeek dayOfWeek, LocalDate date);

    /**
     * True if another schedule of the same doctor on the same weekday overlaps in BOTH
     * time-of-day and validity period. Pass excludeId = -1 when creating.
     * validTo must be non-null here: callers replace "open-ended" with FAR_FUTURE.
     */
    @Query("""
            SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
            FROM DoctorSchedule s
            WHERE s.doctor.id = :doctorId
              AND s.dayOfWeek = :dayOfWeek
              AND s.startTime < :endTime
              AND s.endTime > :startTime
              AND s.validFrom <= :validTo
              AND (s.validTo IS NULL OR s.validTo >= :validFrom)
              AND s.id <> :excludeId
            """)
    boolean existsOverlap(
            Long doctorId,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            LocalDate validFrom,
            LocalDate validTo,
            Long excludeId);

    /** Create-time overlap check. */
    default boolean existsOverlap(Long doctorId, DayOfWeek dayOfWeek, LocalTime startTime,
                                  LocalTime endTime, LocalDate validFrom, LocalDate validTo) {
        LocalDate to = validTo != null ? validTo : FAR_FUTURE;
        return existsOverlap(doctorId, dayOfWeek, startTime, endTime, validFrom, to, -1L);
    }

    /** Update-time overlap check: ignores the schedule being edited. */
    default boolean existsOverlapExcluding(Long doctorId, DayOfWeek dayOfWeek, LocalTime startTime,
                                           LocalTime endTime, LocalDate validFrom, LocalDate validTo,
                                           Long excludeId) {
        LocalDate to = validTo != null ? validTo : FAR_FUTURE;
        return existsOverlap(doctorId, dayOfWeek, startTime, endTime, validFrom, to, excludeId);
    }
}
