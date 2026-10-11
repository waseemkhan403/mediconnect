package com.mediconnect.service;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.dto.doctor.DoctorScheduleRequest;
import com.mediconnect.dto.doctor.DoctorScheduleResponse;
import com.mediconnect.entity.Doctor;
import com.mediconnect.entity.DoctorSchedule;
import com.mediconnect.exception.AlreadyExistException;
import com.mediconnect.exception.BadRequestException;
import com.mediconnect.exception.ForbiddenException;
import com.mediconnect.exception.NotFoundException;
import com.mediconnect.repository.DoctorRepository;
import com.mediconnect.repository.DoctorScheduleRepository;
import com.mediconnect.response.ResponseHandler;
import com.mediconnect.response.Status;
import com.mediconnect.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Weekly schedule templates, always addressed through their doctor (/doctors/{doctorId}/schedules/{id}),
 * so one doctor can never read or change another doctor's schedule by guessing ids.
 */
@Service
@RequiredArgsConstructor
public class DoctorScheduleService {

    private final DoctorScheduleRepository scheduleRepository;
    private final DoctorRepository doctorRepository;
    private final CurrentUser currentUser;
    private final MessageUtils messageUtils;

    @Transactional(readOnly = true)
    public ResponseEntity<Object> getSchedules(Long doctorId) {
        if (!doctorRepository.existsById(doctorId)) {
            throw new NotFoundException(messageUtils.getMessage("doctor.notfound"));
        }
        // day_of_week is text in the database, so order by the real weekday here.
        List<DoctorScheduleResponse> schedules = scheduleRepository.findByDoctorId(doctorId).stream()
                .sorted(Comparator.comparing(DoctorSchedule::getDayOfWeek)
                        .thenComparing(DoctorSchedule::getStartTime)
                        .thenComparing(DoctorSchedule::getValidFrom))
                .map(DoctorScheduleResponse::from)
                .toList();
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                schedules,
                new Status(200, messageUtils.getMessage("schedule.list.success")));
    }

    @Transactional
    public ResponseEntity<Object> addSchedule(Long doctorId, DoctorScheduleRequest request) {
        checkCanEdit(doctorId);
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("doctor.notfound")));
        LocalTime start = request.startTime().truncatedTo(ChronoUnit.MINUTES);
        LocalTime end = request.endTime().truncatedTo(ChronoUnit.MINUTES);
        validate(doctor, request, start, end, -1L);

        DoctorSchedule saved = scheduleRepository.save(DoctorSchedule.builder()
                .doctor(doctor)
                .dayOfWeek(request.dayOfWeek())
                .startTime(start)
                .endTime(end)
                .slotDurationMinutes(request.slotDurationMinutes())
                .validFrom(request.validFrom())
                .validTo(request.validTo())
                .build());
        return ResponseHandler.generateResponse(
                HttpStatus.CREATED,
                DoctorScheduleResponse.from(saved),
                new Status(201, messageUtils.getMessage("schedule.add.success")));
    }

    @Transactional
    public ResponseEntity<Object> updateSchedule(Long doctorId, Long scheduleId, DoctorScheduleRequest request) {
        checkCanEdit(doctorId);
        DoctorSchedule existing = findOwned(doctorId, scheduleId);
        LocalTime start = request.startTime().truncatedTo(ChronoUnit.MINUTES);
        LocalTime end = request.endTime().truncatedTo(ChronoUnit.MINUTES);
        validate(existing.getDoctor(), request, start, end, scheduleId);

        existing.setDayOfWeek(request.dayOfWeek());
        existing.setStartTime(start);
        existing.setEndTime(end);
        existing.setSlotDurationMinutes(request.slotDurationMinutes());
        existing.setValidFrom(request.validFrom());
        existing.setValidTo(request.validTo());

        DoctorSchedule saved = scheduleRepository.save(existing);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                DoctorScheduleResponse.from(saved),
                new Status(200, messageUtils.getMessage("schedule.update.success")));
    }

    @Transactional
    public ResponseEntity<Object> deleteSchedule(Long doctorId, Long scheduleId) {
        checkCanEdit(doctorId);
        DoctorSchedule existing = findOwned(doctorId, scheduleId);
        scheduleRepository.delete(existing);
        scheduleRepository.flush();
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                null,
                new Status(200, messageUtils.getMessage("schedule.delete.success")));
    }

    /**
     * Doctors may change only their own schedule (admins any). 403 rather than 404: schedules are not secret,
     * every staff member can read them.
     */
    private void checkCanEdit(Long doctorId) {
        if (currentUser.isDoctor() && !doctorId.equals(currentUser.doctorId())) {
            throw new ForbiddenException(messageUtils.getMessage("schedule.notown"));
        }
    }

    /** The schedule must exist AND belong to this doctor; otherwise it is "not found" for this URL. */
    private DoctorSchedule findOwned(Long doctorId, Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .filter(s -> s.getDoctor().getId().equals(doctorId))
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("schedule.notfound")));
    }

    /** Cross-field rules the DTO cannot express. excludeId = -1 when creating. */
    private void validate(Doctor doctor, DoctorScheduleRequest r, LocalTime start, LocalTime end, Long excludeId) {
        if (!end.isAfter(start)) {
            throw new BadRequestException(messageUtils.getMessage("schedule.time.invalid"));
        }
        if (r.validTo() != null && r.validTo().isBefore(r.validFrom())) {
            throw new BadRequestException(messageUtils.getMessage("schedule.validity.invalid"));
        }
        long windowMinutes = Duration.between(start, end).toMinutes();
        int slot = r.slotDurationMinutes();
        if (windowMinutes < slot) {
            throw new BadRequestException(messageUtils.getMessage("schedule.slot.toolong", String.valueOf(slot), String.valueOf(windowMinutes)));
        }
        // A window that is not a whole number of slots would silently waste its tail; make the admin fix it.
        if (windowMinutes % slot != 0) {
            throw new BadRequestException(messageUtils.getMessage("schedule.slot.notmultiple", String.valueOf(windowMinutes), String.valueOf(slot)));
        }
        if (scheduleRepository.existsOverlapExcluding(
                doctor.getId(), r.dayOfWeek(), start, end, r.validFrom(), r.validTo(), excludeId)) {
            throw new AlreadyExistException(messageUtils.getMessage("schedule.overlap"));
        }
    }
}
