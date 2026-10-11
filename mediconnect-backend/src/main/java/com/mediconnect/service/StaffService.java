package com.mediconnect.service;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.dto.staff.StaffFiltersRequest;
import com.mediconnect.dto.staff.StaffListResponse;
import com.mediconnect.dto.staff.StaffRequest;
import com.mediconnect.dto.staff.StaffResponse;
import com.mediconnect.entity.Department;
import com.mediconnect.entity.Staff;
import com.mediconnect.exception.AlreadyExistException;
import com.mediconnect.exception.BadRequestException;
import com.mediconnect.exception.NotFoundException;
import com.mediconnect.repository.DepartmentRepository;
import com.mediconnect.repository.StaffRepository;
import com.mediconnect.response.PageResponse;
import com.mediconnect.response.ResponseHandler;
import com.mediconnect.response.Status;
import com.mediconnect.util.Texts;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Non-doctor staff. Rows with staffType DOCTOR belong to DoctorService (it keeps the Staff and
 * Doctor rows in step), so this service refuses to edit or delete them.
 */
@Service
@RequiredArgsConstructor
public class StaffService {

    private static final String DOCTOR = "DOCTOR";

    private final StaffRepository staffRepository;
    private final DepartmentRepository departmentRepository;
    private final MessageUtils messageUtils;

    @Transactional(readOnly = true)
    public ResponseEntity<Object> getStaffList(StaffFiltersRequest filters, Pageable pageable) {
        if (filters == null) {
            filters = new StaffFiltersRequest(null, null, null, null);
        }
        Page<StaffListResponse> page = staffRepository.findAllWithFilters(
                        Texts.clean(filters.name()),
                        Texts.clean(filters.staffType()),
                        Texts.clean(filters.status()),
                        filters.departmentId(),
                        pageable)
                .map(StaffListResponse::from);
        return ResponseHandler.generatePageResponse(
                HttpStatus.OK,
                PageResponse.from(page),
                new Status(200, messageUtils.getMessage("staff.list.success")));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Object> getStaffById(Long id) {
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                StaffResponse.from(findDetail(id)),
                new Status(200, messageUtils.getMessage("staff.get.success", String.valueOf(id))));
    }

    @Transactional
    public ResponseEntity<Object> addStaff(StaffRequest request) {
        String code = request.staffCode().trim();
        String email = Texts.email(request.email());

        if (staffRepository.existsByStaffCode(code)) {
            throw new AlreadyExistException(messageUtils.getMessage("staff.code.exists", code));
        }
        if (staffRepository.existsByEmailIgnoreCase(email)) {
            throw new AlreadyExistException(messageUtils.getMessage("staff.email.exists", email));
        }

        Staff saved = staffRepository.save(Staff.builder()
                .staffCode(code)
                .fullName(request.fullName().trim())
                .gender(request.gender())
                .dateOfBirth(request.dateOfBirth())
                .phone(Texts.phone(request.phone()))
                .email(email)
                .staffType(request.staffType())
                .designation(Texts.clean(request.designation()))
                .department(resolveDepartment(request.departmentId()))
                .joiningDate(request.joiningDate())
                .status(request.status() != null ? request.status() : "ACTIVE")
                .build());
        return ResponseHandler.generateResponse(
                HttpStatus.CREATED,
                StaffResponse.from(saved),
                new Status(201, messageUtils.getMessage("staff.add.success")));
    }

    @Transactional
    public ResponseEntity<Object> updateStaff(Long id, StaffRequest request) {
        Staff existing = findDetail(id);
        if (DOCTOR.equals(existing.getStaffType())) {
            throw new BadRequestException(messageUtils.getMessage("staff.isdoctor"));
        }

        String code = request.staffCode().trim();
        String email = Texts.email(request.email());

        boolean codeTaken = staffRepository.findByStaffCode(code)
                .filter(other -> !other.getId().equals(id))
                .isPresent();
        if (codeTaken) {
            throw new AlreadyExistException(messageUtils.getMessage("staff.code.exists", code));
        }
        if (staffRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new AlreadyExistException(messageUtils.getMessage("staff.email.exists", email));
        }

        existing.setStaffCode(code);
        existing.setFullName(request.fullName().trim());
        existing.setGender(request.gender());
        existing.setDateOfBirth(request.dateOfBirth());
        existing.setPhone(Texts.phone(request.phone()));
        existing.setEmail(email);
        existing.setStaffType(request.staffType());
        existing.setDesignation(Texts.clean(request.designation()));
        existing.setDepartment(resolveDepartment(request.departmentId()));
        existing.setJoiningDate(request.joiningDate());
        if (request.status() != null) {
            existing.setStatus(request.status());
        }

        Staff saved = staffRepository.save(existing);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                StaffResponse.from(saved),
                new Status(200, messageUtils.getMessage("staff.update.success")));
    }

    /**
     * Hard delete is for mistaken entries. Staff who worked at the hospital should be set to INACTIVE
     * (status field on update) so their history stays; later tables (leave, shifts) will block this delete.
     */
    @Transactional
    public ResponseEntity<Object> deleteStaff(Long id) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("staff.notfound")));
        if (DOCTOR.equals(staff.getStaffType())) {
            throw new BadRequestException(messageUtils.getMessage("staff.isdoctor"));
        }
        staffRepository.delete(staff);
        staffRepository.flush();
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                null,
                new Status(200, messageUtils.getMessage("staff.delete.success")));
    }

    private Staff findDetail(Long id) {
        return staffRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("staff.notfound")));
    }

    /** departmentId is optional for staff; when given it must exist. */
    private Department resolveDepartment(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("department.notfound")));
    }
}
