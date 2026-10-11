package com.mediconnect.service;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.dto.appointment.AppointmentFiltersRequest;
import com.mediconnect.dto.appointment.AppointmentRequest;
import com.mediconnect.dto.appointment.AppointmentRescheduleRequest;
import com.mediconnect.dto.appointment.AppointmentResponse;
import com.mediconnect.dto.appointment.AppointmentStatusRequest;
import com.mediconnect.dto.appointment.AppointmentUpdateRequest;
import com.mediconnect.dto.appointment.DoctorSlotsResponse;
import com.mediconnect.dto.appointment.DoctorSlotsResponse.Slot;
import com.mediconnect.entity.Appointment;
import com.mediconnect.entity.Doctor;
import com.mediconnect.entity.DoctorSchedule;
import com.mediconnect.entity.Patient;
import com.mediconnect.exception.AlreadyExistException;
import com.mediconnect.exception.BadRequestException;
import com.mediconnect.exception.ForbiddenException;
import com.mediconnect.exception.NotFoundException;
import com.mediconnect.helper.HospitalZone;
import com.mediconnect.repository.AppointmentRepository;
import com.mediconnect.repository.DoctorRepository;
import com.mediconnect.repository.DoctorScheduleRepository;
import com.mediconnect.repository.PatientRepository;
import com.mediconnect.repository.StaffLeaveRepository;
import com.mediconnect.response.PageResponse;
import com.mediconnect.response.ResponseHandler;
import com.mediconnect.response.Status;
import com.mediconnect.security.CurrentUser;
import com.mediconnect.util.Texts;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Booking rules. Free slots are computed from the doctor's weekly schedules at read time; only bookings are stored.
 * A booking must be a slot the schedule really produces, in the future, within the booking window, with a verified
 * and active doctor, an active patient, and no overlapping active booking for either of them.
 * The partial unique index on (doctor_id, slot_start) in schema.sql is the final guard when two requests race.
 */
@Service
@RequiredArgsConstructor
public class AppointmentService {

    /** Bookable from today up to this many days ahead (local hospital dates). */
    static final int BOOKING_WINDOW_DAYS = 30;

    /** Wide bounds used when the list has no date filter (keeps the query free of null instants). */
    private static final Instant MIN_INSTANT = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant MAX_INSTANT = Instant.parse("9999-12-31T00:00:00Z");

    /** Allowed status moves. A status that is not a key (COMPLETED, CANCELLED, NO_SHOW, RESCHEDULED) is final. */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "REQUESTED", Set.of("CONFIRMED", "CANCELLED"),
            "CONFIRMED", Set.of("CHECKED_IN", "CANCELLED", "NO_SHOW"),
            "CHECKED_IN", Set.of("IN_CONSULTATION"),
            "IN_CONSULTATION", Set.of("COMPLETED"));

    private static final Set<String> RESCHEDULABLE = Set.of("REQUESTED", "CONFIRMED");

    /**
     * Statuses each role may set (other staff may set any). A patient can only cancel; a doctor runs the
     * consultation; arrival (CHECKED_IN) is recorded by the front desk or nurses.
     */
    private static final Map<String, Set<String>> STATUS_BY_ROLE = Map.of(
            "PATIENT", Set.of("CANCELLED"),
            "DOCTOR", Set.of("IN_CONSULTATION", "COMPLETED", "NO_SHOW"));

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final PatientRepository patientRepository;
    private final StaffLeaveRepository leaveRepository;
    private final HospitalZone hospitalZone;
    private final CurrentUser currentUser;
    private final MessageUtils messageUtils;

    // -------------------------------------------------------------------------
    // FREE SLOTS (public): what a patient can pick from
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getAvailableSlots(Long doctorId, LocalDate date) {
        Doctor doctor = findDoctor(doctorId);
        // The public site must not reveal unverified or inactive doctors, so they are simply "not found" here.
        if (!isBookable(doctor)) {
            throw new NotFoundException(messageUtils.getMessage("doctor.notfound"));
        }
        ZoneId zone = hospitalZone.get();
        checkBookingWindow(date, zone);
        // Approved leave: no slots that day. The message says why, without exposing leave details publicly.
        if (leaveRepository.isOnApprovedLeave(doctor.getStaff().getId(), date)) {
            return ResponseHandler.generateResponse(
                    HttpStatus.OK,
                    new DoctorSlotsResponse(doctorId, date, List.of()),
                    new Status(200, messageUtils.getMessage("appointment.slots.unavailable")));
        }

        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<Appointment> taken = appointmentRepository.findActiveForDoctor(doctorId, dayStart, dayEnd);
        Instant now = Instant.now();

        List<Slot> free = generateSlots(doctorId, date).stream()
                .filter(slot -> {
                    Instant start = toInstant(date, slot.startTime(), zone);
                    Instant end = toInstant(date, slot.endTime(), zone);
                    return start.isAfter(now) && taken.stream().noneMatch(
                            a -> a.getSlotStart().isBefore(end) && a.getSlotEnd().isAfter(start));
                })
                .toList();
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                new DoctorSlotsResponse(doctorId, date, free),
                new Status(200, messageUtils.getMessage("appointment.slots.success")));
    }

    // -------------------------------------------------------------------------
    // GET ALL (paginated + filtered). Patient and doctor dashboards filter by patientId / doctorId.
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getAppointments(AppointmentFiltersRequest filters, Pageable pageable) {
        if (filters == null) {
            filters = new AppointmentFiltersRequest(null, null, null, null, null);
        }
        if (filters.dateFrom() != null && filters.dateTo() != null && filters.dateTo().isBefore(filters.dateFrom())) {
            throw new BadRequestException(messageUtils.getMessage("appointment.filter.dates"));
        }
        ZoneId zone = hospitalZone.get();
        Instant from = filters.dateFrom() != null ? filters.dateFrom().atStartOfDay(zone).toInstant() : MIN_INSTANT;
        // dateTo is inclusive: everything before the start of the next day.
        Instant to = filters.dateTo() != null ? filters.dateTo().plusDays(1).atStartOfDay(zone).toInstant() : MAX_INSTANT;

        // Forced server-side, whatever the body says: a doctor sees their own bookings, a patient their own records'.
        Long doctorId = currentUser.isDoctor() ? currentUser.doctorId() : filters.doctorId();
        Long ownerId = currentUser.isPatient() ? currentUser.id() : null;
        Page<AppointmentResponse> page = appointmentRepository.findAllWithFilters(
                        doctorId,
                        filters.patientId(),
                        ownerId,
                        Texts.clean(filters.status()),
                        from,
                        to,
                        pageable)
                .map(a -> AppointmentResponse.from(a, zone));
        return ResponseHandler.generatePageResponse(
                HttpStatus.OK,
                PageResponse.from(page),
                new Status(200, messageUtils.getMessage("appointment.list.success")));
    }

    // -------------------------------------------------------------------------
    // GET BY ID
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getAppointmentById(Long id) {
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                AppointmentResponse.from(findAccessible(id), hospitalZone.get()),
                new Status(200, messageUtils.getMessage("appointment.get.success", String.valueOf(id))));
    }

    // -------------------------------------------------------------------------
    // BOOK (confirmed straight away: the slot was checked to be free)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> addAppointment(AppointmentRequest request) {
        Patient patient = findPatient(request.patientId());
        // A patient login may book only for patient records it owns (itself or its dependents).
        if (currentUser.isPatient() && !ownedByCaller(patient)) {
            throw new NotFoundException(messageUtils.getMessage("patient.notfound"));
        }
        Doctor doctor = findDoctor(request.doctorId());
        checkParticipants(patient, doctor);

        ZoneId zone = hospitalZone.get();
        Slot slot = resolveSlot(doctor, request.date(), request.startTime(), zone);
        Instant start = toInstant(request.date(), slot.startTime(), zone);
        Instant end = toInstant(request.date(), slot.endTime(), zone);
        checkFree(doctor.getId(), patient.getId(), start, end);

        Appointment saved = saveBooking(Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .slotStart(start)
                .slotEnd(end)
                .status("CONFIRMED")
                .reason(Texts.clean(request.reason()))
                .build());
        return ResponseHandler.generateResponse(
                HttpStatus.CREATED,
                AppointmentResponse.from(saved, zone),
                new Status(201, messageUtils.getMessage("appointment.add.success")));
    }

    // -------------------------------------------------------------------------
    // UPDATE (editable details only: the reason)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> updateAppointment(Long id, AppointmentUpdateRequest request) {
        Appointment appointment = findAccessible(id);
        if (!TRANSITIONS.containsKey(appointment.getStatus())) {
            throw new BadRequestException(messageUtils.getMessage("appointment.closed", appointment.getStatus()));
        }
        appointment.setReason(Texts.clean(request.reason()));
        Appointment saved = appointmentRepository.save(appointment);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                AppointmentResponse.from(saved, hospitalZone.get()),
                new Status(200, messageUtils.getMessage("appointment.update.success")));
    }

    // -------------------------------------------------------------------------
    // STATUS (state machine; see TRANSITIONS)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> updateAppointmentStatus(Long id, AppointmentStatusRequest request) {
        Appointment appointment = findAccessible(id);
        String from = appointment.getStatus();
        String to = request.status();
        Set<String> allowedForRole = STATUS_BY_ROLE.get(currentUser.role());
        if (allowedForRole != null && !allowedForRole.contains(to)) {
            throw new ForbiddenException(messageUtils.getMessage("appointment.status.role", to));
        }
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new BadRequestException(messageUtils.getMessage("appointment.status.transition", from, to));
        }

        ZoneId zone = hospitalZone.get();
        Instant now = Instant.now();
        switch (to) {
            case "CANCELLED" -> {
                if (!now.isBefore(appointment.getSlotStart())) {
                    throw new BadRequestException(messageUtils.getMessage("appointment.cancel.started"));
                }
                appointment.setCancellationReason(Texts.clean(request.cancellationReason()));
            }
            case "NO_SHOW" -> {
                if (now.isBefore(appointment.getSlotStart())) {
                    throw new BadRequestException(messageUtils.getMessage("appointment.noshow.early"));
                }
            }
            case "CHECKED_IN" -> {
                LocalDate day = appointment.getSlotStart().atZone(zone).toLocalDate();
                if (!day.equals(LocalDate.now(zone))) {
                    throw new BadRequestException(messageUtils.getMessage("appointment.checkin.nottoday"));
                }
            }
            default -> {
                // CONFIRMED, IN_CONSULTATION, COMPLETED: the transition table is the only rule.
            }
        }
        appointment.setStatus(to);

        Appointment saved = appointmentRepository.save(appointment);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                AppointmentResponse.from(saved, zone),
                new Status(200, messageUtils.getMessage("appointment.status.success", to)));
    }

    // -------------------------------------------------------------------------
    // RESCHEDULE (same doctor): the old booking becomes RESCHEDULED, a new CONFIRMED one points back to it
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> rescheduleAppointment(Long id, AppointmentRescheduleRequest request) {
        Appointment old = findAccessible(id);
        if (!RESCHEDULABLE.contains(old.getStatus())) {
            throw new BadRequestException(messageUtils.getMessage("appointment.reschedule.notallowed", old.getStatus()));
        }
        if (!Instant.now().isBefore(old.getSlotStart())) {
            throw new BadRequestException(messageUtils.getMessage("appointment.reschedule.started"));
        }
        Patient patient = old.getPatient();
        Doctor doctor = old.getDoctor();
        checkParticipants(patient, doctor);

        ZoneId zone = hospitalZone.get();
        Slot slot = resolveSlot(doctor, request.date(), request.startTime(), zone);
        Instant start = toInstant(request.date(), slot.startTime(), zone);
        Instant end = toInstant(request.date(), slot.endTime(), zone);
        if (start.equals(old.getSlotStart())) {
            throw new BadRequestException(messageUtils.getMessage("appointment.reschedule.sameslot"));
        }

        // Release the old slot first so it does not count as a clash. If anything below fails,
        // the transaction rolls back and the old booking is untouched.
        old.setStatus("RESCHEDULED");
        appointmentRepository.saveAndFlush(old);
        checkFree(doctor.getId(), patient.getId(), start, end);

        Appointment saved = saveBooking(Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .slotStart(start)
                .slotEnd(end)
                .status("CONFIRMED")
                .reason(old.getReason())
                .rescheduledFrom(old)
                .build());
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                AppointmentResponse.from(saved, zone),
                new Status(200, messageUtils.getMessage("appointment.reschedule.success", String.valueOf(saved.getId()))));
    }

    // -------------------------------------------------------------------------
    // DELETE (mistaken entries only; cancel real bookings instead so the history stays)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> deleteAppointment(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("appointment.notfound")));
        appointmentRepository.delete(appointment);
        // Flush so a constraint failure (a newer booking rescheduled from this one) surfaces here.
        appointmentRepository.flush();
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                null,
                new Status(200, messageUtils.getMessage("appointment.delete.success")));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    /** Every slot the doctor's schedules produce on this date, booked or not, ordered by time. */
    private List<Slot> generateSlots(Long doctorId, LocalDate date) {
        List<Slot> slots = new ArrayList<>();
        for (DoctorSchedule s : scheduleRepository.findEffective(doctorId, date.getDayOfWeek(), date)) {
            int length = s.getSlotDurationMinutes();
            // Count slots instead of looping on LocalTime: LocalTime wraps at midnight and would never pass the end.
            long count = Duration.between(s.getStartTime(), s.getEndTime()).toMinutes() / length;
            for (int i = 0; i < count; i++) {
                LocalTime start = s.getStartTime().plusMinutes((long) i * length);
                slots.add(new Slot(start, start.plusMinutes(length)));
            }
        }
        slots.sort(Comparator.comparing(Slot::startTime));
        return slots;
    }

    /**
     * The requested time must be one of the doctor's real slots on that date, inside the window, in the future,
     * and not on a day of approved leave.
     */
    private Slot resolveSlot(Doctor doctor, LocalDate date, LocalTime startTime, ZoneId zone) {
        checkBookingWindow(date, zone);
        if (leaveRepository.isOnApprovedLeave(doctor.getStaff().getId(), date)) {
            throw new BadRequestException(messageUtils.getMessage("appointment.doctor.onleave"));
        }
        LocalTime wanted = startTime.truncatedTo(ChronoUnit.MINUTES);
        Slot slot = generateSlots(doctor.getId(), date).stream()
                .filter(s -> s.startTime().equals(wanted))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(messageUtils.getMessage("appointment.slot.invalid")));
        if (!toInstant(date, slot.startTime(), zone).isAfter(Instant.now())) {
            throw new BadRequestException(messageUtils.getMessage("appointment.slot.past"));
        }
        return slot;
    }

    private void checkBookingWindow(LocalDate date, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        if (date.isBefore(today)) {
            throw new BadRequestException(messageUtils.getMessage("appointment.date.past"));
        }
        if (date.isAfter(today.plusDays(BOOKING_WINDOW_DAYS))) {
            throw new BadRequestException(messageUtils.getMessage(
                    "appointment.date.toofar", String.valueOf(BOOKING_WINDOW_DAYS)));
        }
    }

    private void checkParticipants(Patient patient, Doctor doctor) {
        if (!patient.isActive()) {
            throw new BadRequestException(messageUtils.getMessage("appointment.patient.inactive"));
        }
        if (!isBookable(doctor)) {
            throw new BadRequestException(messageUtils.getMessage("appointment.doctor.unavailable"));
        }
    }

    private void checkFree(Long doctorId, Long patientId, Instant start, Instant end) {
        if (appointmentRepository.existsDoctorOverlap(doctorId, start, end)) {
            throw new AlreadyExistException(messageUtils.getMessage("appointment.slot.taken"));
        }
        if (appointmentRepository.existsPatientOverlap(patientId, start, end)) {
            throw new AlreadyExistException(messageUtils.getMessage("appointment.patient.busy"));
        }
    }

    /**
     * Two requests can pass checkFree at the same moment; the unique index then rejects the second insert.
     * Flushing here turns that into a clear "slot taken" 409 instead of the generic constraint message.
     */
    private Appointment saveBooking(Appointment appointment) {
        try {
            return appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            throw new AlreadyExistException(messageUtils.getMessage("appointment.slot.taken"));
        }
    }

    private boolean isBookable(Doctor doctor) {
        return doctor.isVerified() && "ACTIVE".equals(doctor.getStaff().getStatus());
    }

    private static Instant toInstant(LocalDate date, LocalTime time, ZoneId zone) {
        return ZonedDateTime.of(date, time, zone).toInstant();
    }

    private Doctor findDoctor(Long id) {
        return doctorRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("doctor.notfound")));
    }

    private Patient findPatient(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("patient.notfound")));
    }

    private Appointment findDetail(Long id) {
        return appointmentRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("appointment.notfound")));
    }

    /**
     * findDetail + record access: a patient login reaches bookings of the patient records it owns, a doctor
     * their own bookings. 404 rather than 403, so probing ids reveals nothing about other people's bookings.
     */
    private Appointment findAccessible(Long id) {
        Appointment appointment = findDetail(id);
        boolean allowed;
        if (currentUser.isPatient()) {
            allowed = ownedByCaller(appointment.getPatient());
        } else if (currentUser.isDoctor()) {
            allowed = appointment.getDoctor().getId().equals(currentUser.doctorId());
        } else {
            allowed = true;
        }
        if (!allowed) {
            throw new NotFoundException(messageUtils.getMessage("appointment.notfound"));
        }
        return appointment;
    }

    /** getOwner().getId() reads the id from the lazy proxy without a query. */
    private boolean ownedByCaller(Patient patient) {
        return patient.getOwner() != null && patient.getOwner().getId().equals(currentUser.id());
    }
}
