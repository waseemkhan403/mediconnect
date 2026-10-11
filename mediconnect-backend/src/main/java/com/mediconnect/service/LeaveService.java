package com.mediconnect.service;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.dto.appointment.AppointmentResponse;
import com.mediconnect.dto.leave.LeaveFiltersRequest;
import com.mediconnect.dto.leave.LeaveRequest;
import com.mediconnect.dto.leave.LeaveResponse;
import com.mediconnect.dto.leave.LeaveStatusRequest;
import com.mediconnect.entity.Appointment;
import com.mediconnect.entity.Staff;
import com.mediconnect.entity.StaffLeave;
import com.mediconnect.exception.AlreadyExistException;
import com.mediconnect.exception.BadRequestException;
import com.mediconnect.exception.ForbiddenException;
import com.mediconnect.exception.NotFoundException;
import com.mediconnect.helper.HospitalZone;
import com.mediconnect.repository.AppointmentRepository;
import com.mediconnect.repository.DoctorRepository;
import com.mediconnect.repository.StaffLeaveRepository;
import com.mediconnect.repository.StaffRepository;
import com.mediconnect.response.PageResponse;
import com.mediconnect.response.ResponseHandler;
import com.mediconnect.response.Status;
import com.mediconnect.security.CurrentUser;
import com.mediconnect.util.Texts;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Staff leave. Staff apply for themselves (PENDING); an admin approves or rejects. Leave an admin records is
 * APPROVED at once (the admin is the approver). Approved leave of a doctor removes their slots on those days
 * (AppointmentService checks it); existing bookings are NOT cancelled automatically, because patients cannot be
 * notified yet. Reception sees them through getAffectedAppointments and reschedules them.
 * Visibility: admins and reception see every leave; everyone else only their own (others are "not found").
 */
@Service
@RequiredArgsConstructor
public class LeaveService {

    /** Wide bounds used when the list has no date filter. */
    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

    private final StaffLeaveRepository leaveRepository;
    private final StaffRepository staffRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final HospitalZone hospitalZone;
    private final CurrentUser currentUser;
    private final MessageUtils messageUtils;

    // -------------------------------------------------------------------------
    // GET ALL (paginated + filtered)
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getLeaves(LeaveFiltersRequest filters, Pageable pageable) {
        if (filters == null) {
            filters = new LeaveFiltersRequest(null, null, null, null);
        }
        if (filters.dateFrom() != null && filters.dateTo() != null && filters.dateTo().isBefore(filters.dateFrom())) {
            throw new BadRequestException(messageUtils.getMessage("leave.filter.dates"));
        }
        // Forced server-side: only admins and reception may look at other people's leave.
        Long staffId = seesEveryone() ? filters.staffId() : currentUser.staffId();
        Page<LeaveResponse> page = leaveRepository.findAllWithFilters(
                        staffId,
                        Texts.clean(filters.status()),
                        filters.dateFrom() != null ? filters.dateFrom() : MIN_DATE,
                        filters.dateTo() != null ? filters.dateTo() : MAX_DATE,
                        pageable)
                .map(LeaveResponse::from);
        return ResponseHandler.generatePageResponse(
                HttpStatus.OK,
                PageResponse.from(page),
                new Status(200, messageUtils.getMessage("leave.list.success")));
    }

    // -------------------------------------------------------------------------
    // GET BY ID
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getLeaveById(Long id) {
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                LeaveResponse.from(findReadable(id)),
                new Status(200, messageUtils.getMessage("leave.get.success", String.valueOf(id))));
    }

    // -------------------------------------------------------------------------
    // BOOKINGS INSIDE A LEAVE (for reception to reschedule; also useful before approving)
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getAffectedAppointments(Long id) {
        StaffLeave leave = findReadable(id);
        ZoneId zone = hospitalZone.get();
        List<AppointmentResponse> appointments = affectedAppointments(leave, zone).stream()
                .map(a -> AppointmentResponse.from(a, zone))
                .toList();
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                appointments,
                new Status(200, messageUtils.getMessage("leave.appointments.success")));
    }

    // -------------------------------------------------------------------------
    // APPLY (or, for an admin, record approved leave)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> addLeave(LeaveRequest request) {
        boolean admin = currentUser.isAdmin();
        Long staffId = admin && request.staffId() != null ? request.staffId() : currentUser.staffId();
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("staff.notfound")));
        validateDates(staffId, request.startDate(), request.endDate(), -1L);

        StaffLeave saved = leaveRepository.save(StaffLeave.builder()
                .staff(staff)
                .leaveType(request.leaveType())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .reason(Texts.clean(request.reason()))
                .status(admin ? "APPROVED" : "PENDING")
                .build());
        String message = admin
                ? approvedMessage(saved)
                : messageUtils.getMessage("leave.add.success");
        return ResponseHandler.generateResponse(
                HttpStatus.CREATED,
                LeaveResponse.from(saved),
                new Status(201, message));
    }

    // -------------------------------------------------------------------------
    // UPDATE (while PENDING, by the person or an admin)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> updateLeave(Long id, LeaveRequest request) {
        StaffLeave leave = findReadable(id);
        if (!isOwner(leave) && !currentUser.isAdmin()) {
            throw new ForbiddenException(messageUtils.getMessage("leave.notown"));
        }
        if (!"PENDING".equals(leave.getStatus())) {
            throw new BadRequestException(messageUtils.getMessage("leave.notpending", leave.getStatus()));
        }
        validateDates(leave.getStaff().getId(), request.startDate(), request.endDate(), id);

        leave.setLeaveType(request.leaveType());
        leave.setStartDate(request.startDate());
        leave.setEndDate(request.endDate());
        leave.setReason(Texts.clean(request.reason()));
        StaffLeave saved = leaveRepository.save(leave);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                LeaveResponse.from(saved),
                new Status(200, messageUtils.getMessage("leave.update.success")));
    }

    // -------------------------------------------------------------------------
    // STATUS: approve / reject (admin), cancel (the person or an admin)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> updateLeaveStatus(Long id, LeaveStatusRequest request) {
        StaffLeave leave = findReadable(id);
        String from = leave.getStatus();
        String to = request.status();
        boolean admin = currentUser.isAdmin();

        if ("CANCELLED".equals(to)) {
            if (!isOwner(leave) && !admin) {
                throw new ForbiddenException(messageUtils.getMessage("leave.notown"));
            }
            if (!"PENDING".equals(from) && !"APPROVED".equals(from)) {
                throw new BadRequestException(messageUtils.getMessage("leave.status.transition", from, to));
            }
            // Staff may withdraw approved leave only before it starts; an admin may also end it early.
            if ("APPROVED".equals(from) && !admin
                    && !LocalDate.now(hospitalZone.get()).isBefore(leave.getStartDate())) {
                throw new BadRequestException(messageUtils.getMessage("leave.cancel.started"));
            }
        } else {
            if (!admin) {
                throw new ForbiddenException(messageUtils.getMessage("leave.review.admin"));
            }
            if (!"PENDING".equals(from)) {
                throw new BadRequestException(messageUtils.getMessage("leave.status.transition", from, to));
            }
        }
        leave.setStatus(to);
        leave.setReviewNote(Texts.clean(request.reviewNote()));
        StaffLeave saved = leaveRepository.save(leave);

        String message = "APPROVED".equals(to)
                ? approvedMessage(saved)
                : messageUtils.getMessage("leave.status.success", to);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                LeaveResponse.from(saved),
                new Status(200, message));
    }

    // -------------------------------------------------------------------------
    // DELETE (admin, mistaken entries; cancel real leave instead so the history stays)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> deleteLeave(Long id) {
        StaffLeave leave = leaveRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("leave.notfound")));
        leaveRepository.delete(leave);
        leaveRepository.flush();
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                null,
                new Status(200, messageUtils.getMessage("leave.delete.success")));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    /** End not before start; no overlap with the person's other pending/approved leave; staff cannot backdate. */
    private void validateDates(Long staffId, LocalDate start, LocalDate end, Long excludeId) {
        if (end.isBefore(start)) {
            throw new BadRequestException(messageUtils.getMessage("leave.dates.invalid"));
        }
        // Admins may record leave after the fact (e.g. sick leave reported later); staff apply in advance.
        if (!currentUser.isAdmin() && start.isBefore(LocalDate.now(hospitalZone.get()))) {
            throw new BadRequestException(messageUtils.getMessage("leave.dates.past"));
        }
        if (leaveRepository.existsOverlap(staffId, start, end, excludeId)) {
            throw new AlreadyExistException(messageUtils.getMessage("leave.overlap"));
        }
    }

    /** Approval message that tells the admin how many bookings now need rescheduling. */
    private String approvedMessage(StaffLeave leave) {
        int affected = affectedAppointments(leave, hospitalZone.get()).size();
        return affected == 0
                ? messageUtils.getMessage("leave.approved")
                // The count stays a number here: the message uses a choice format for singular/plural.
                : messageUtils.getMessage("leave.approved.affected", affected, String.valueOf(leave.getId()));
    }

    /** REQUESTED/CONFIRMED bookings of the doctor behind this staff row during the leave (empty for non-doctors). */
    private List<Appointment> affectedAppointments(StaffLeave leave, ZoneId zone) {
        return doctorRepository.findByStaffId(leave.getStaff().getId())
                .map(doctor -> appointmentRepository.findUpcomingForDoctor(
                        doctor.getId(),
                        leave.getStartDate().atStartOfDay(zone).toInstant(),
                        leave.getEndDate().plusDays(1).atStartOfDay(zone).toInstant()))
                .orElse(List.of());
    }

    private boolean seesEveryone() {
        return currentUser.isAdmin() || "RECEPTIONIST".equals(currentUser.role());
    }

    private boolean isOwner(StaffLeave leave) {
        return leave.getStaff().getId().equals(currentUser.staffId());
    }

    /** Someone else's leave is "not found" unless the caller is an admin or reception. */
    private StaffLeave findReadable(Long id) {
        StaffLeave leave = leaveRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("leave.notfound")));
        if (!seesEveryone() && !isOwner(leave)) {
            throw new NotFoundException(messageUtils.getMessage("leave.notfound"));
        }
        return leave;
    }
}
