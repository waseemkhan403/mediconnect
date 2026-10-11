package com.mediconnect.service;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.dto.ActiveStatusRequest;
import com.mediconnect.dto.doctor.DoctorFiltersRequest;
import com.mediconnect.dto.doctor.DoctorListResponse;
import com.mediconnect.dto.doctor.DoctorRequest;
import com.mediconnect.dto.doctor.DoctorResponse;
import com.mediconnect.dto.doctor.DoctorVerificationRequest;
import com.mediconnect.entity.Department;
import com.mediconnect.entity.Doctor;
import com.mediconnect.entity.Staff;
import com.mediconnect.exception.AlreadyExistException;
import com.mediconnect.exception.NotFoundException;
import com.mediconnect.repository.DepartmentRepository;
import com.mediconnect.repository.DoctorRepository;
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

import java.math.BigDecimal;

/**
 * A doctor is a Staff row (person) plus a Doctor row (professional data). Every write here
 * touches both inside ONE transaction, so you can never end up with half a doctor.
 */
@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final StaffRepository staffRepository;
    private final DepartmentRepository departmentRepository;
    private final MessageUtils messageUtils;

    // -------------------------------------------------------------------------
    // GET ALL (paginated + filtered): admin view, includes unverified and inactive doctors.
    // Public pages use getPublicDoctors below.
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getDoctors(DoctorFiltersRequest filters, Pageable pageable) {
        if (filters == null) {
            filters = new DoctorFiltersRequest(null, null, null, null, null);
        }
        Page<DoctorListResponse> page = doctorRepository.findAllWithFilters(
                        Texts.clean(filters.name()),
                        Texts.clean(filters.specialization()),
                        filters.departmentId(),
                        filters.verified(),
                        Texts.clean(filters.status()),
                        pageable)
                .map(DoctorListResponse::from);
        return ResponseHandler.generatePageResponse(
                HttpStatus.OK,
                PageResponse.from(page),
                new Status(200, messageUtils.getMessage("doctor.list.success")));
    }

    // -------------------------------------------------------------------------
    // GET ALL for the public website: only verified AND active doctors, whatever the caller asks for
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getPublicDoctors(DoctorFiltersRequest filters, Pageable pageable) {
        DoctorFiltersRequest f = filters != null
                ? filters
                : new DoctorFiltersRequest(null, null, null, null, null);
        return getDoctors(new DoctorFiltersRequest(
                f.name(), f.specialization(), f.departmentId(), true, "ACTIVE"), pageable);
    }

    // -------------------------------------------------------------------------
    // GET BY ID
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getDoctorById(Long id) {
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                DoctorResponse.from(findDetail(id)),
                new Status(200, messageUtils.getMessage("doctor.get.success", String.valueOf(id))));
    }

    // -------------------------------------------------------------------------
    // CREATE (staff row + doctor row)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> addDoctor(DoctorRequest request) {
        String code = request.staffCode().trim();
        String email = Texts.email(request.email());
        String council = request.registrationCouncil().trim();
        String regNo = request.registrationNumber().trim();

        if (staffRepository.existsByStaffCode(code)) {
            throw new AlreadyExistException(messageUtils.getMessage("staff.code.exists", code));
        }
        if (staffRepository.existsByEmailIgnoreCase(email)) {
            throw new AlreadyExistException(messageUtils.getMessage("staff.email.exists", email));
        }
        if (doctorRepository.existsByRegistrationCouncilAndRegistrationNumber(council, regNo)) {
            throw new AlreadyExistException(messageUtils.getMessage("doctor.registration.exists", regNo, council));
        }
        Department department = resolveDepartment(request.departmentId());

        Staff staff = staffRepository.save(Staff.builder()
                .staffCode(code)
                .fullName(request.fullName().trim())
                .gender(request.gender())
                .dateOfBirth(request.dateOfBirth())
                .phone(Texts.phone(request.phone()))
                .email(email)
                .staffType("DOCTOR")
                .designation(Texts.clean(request.designation()))
                .department(department)
                .joiningDate(request.joiningDate())
                .status("ACTIVE")
                .build());

        Doctor doctor = doctorRepository.save(Doctor.builder()
                .staff(staff)
                .specialization(request.specialization().trim())
                .qualifications(Texts.clean(request.qualifications()))
                .registrationNumber(regNo)
                .registrationCouncil(council)
                .experienceYears(request.experienceYears() != null ? request.experienceYears() : 0)
                .consultationFee(request.consultationFee() != null ? request.consultationFee() : BigDecimal.ZERO)
                .languages(Texts.cleanList(request.languages()))
                .bio(Texts.clean(request.bio()))
                .verified(false)
                .build());

        return ResponseHandler.generateResponse(
                HttpStatus.CREATED,
                DoctorResponse.from(doctor),
                new Status(201, messageUtils.getMessage("doctor.add.success")));
    }

    // -------------------------------------------------------------------------
    // UPDATE (staff row + doctor row)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> updateDoctor(Long id, DoctorRequest request) {
        Doctor doctor = findDetail(id);
        Staff staff = doctor.getStaff();

        String code = request.staffCode().trim();
        String email = Texts.email(request.email());
        String council = request.registrationCouncil().trim();
        String regNo = request.registrationNumber().trim();

        boolean codeTaken = staffRepository.findByStaffCode(code)
                .filter(other -> !other.getId().equals(staff.getId()))
                .isPresent();
        if (codeTaken) {
            throw new AlreadyExistException(messageUtils.getMessage("staff.code.exists", code));
        }
        if (staffRepository.existsByEmailIgnoreCaseAndIdNot(email, staff.getId())) {
            throw new AlreadyExistException(messageUtils.getMessage("staff.email.exists", email));
        }
        if (doctorRepository.existsByRegistrationCouncilAndRegistrationNumberAndIdNot(council, regNo, id)) {
            throw new AlreadyExistException(messageUtils.getMessage("doctor.registration.exists", regNo, council));
        }
        Department department = resolveDepartment(request.departmentId());

        // A verification vouches for one specific registration. Change it and the vouching is void.
        boolean registrationChanged = !council.equals(doctor.getRegistrationCouncil())
                || !regNo.equals(doctor.getRegistrationNumber());

        staff.setStaffCode(code);
        staff.setFullName(request.fullName().trim());
        staff.setGender(request.gender());
        staff.setDateOfBirth(request.dateOfBirth());
        staff.setPhone(Texts.phone(request.phone()));
        staff.setEmail(email);
        staff.setDesignation(Texts.clean(request.designation()));
        staff.setDepartment(department);
        staff.setJoiningDate(request.joiningDate());

        doctor.setSpecialization(request.specialization().trim());
        doctor.setQualifications(Texts.clean(request.qualifications()));
        doctor.setRegistrationNumber(regNo);
        doctor.setRegistrationCouncil(council);
        doctor.setExperienceYears(request.experienceYears() != null ? request.experienceYears() : 0);
        doctor.setConsultationFee(request.consultationFee() != null ? request.consultationFee() : BigDecimal.ZERO);
        doctor.setLanguages(Texts.cleanList(request.languages()));
        doctor.setBio(Texts.clean(request.bio()));
        if (registrationChanged) {
            doctor.setVerified(false);
        }

        staffRepository.save(staff);
        Doctor saved = doctorRepository.save(doctor);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                DoctorResponse.from(saved),
                new Status(200, messageUtils.getMessage(
                        registrationChanged ? "doctor.update.reverify" : "doctor.update.success")));
    }

    // -------------------------------------------------------------------------
    // VERIFY (admin action, separate from editing the profile)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> verifyDoctor(Long id, DoctorVerificationRequest request) {
        Doctor doctor = findDetail(id);
        doctor.setVerified(request.verified());
        Doctor saved = doctorRepository.save(doctor);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                DoctorResponse.from(saved),
                new Status(200, messageUtils.getMessage("doctor.verify.success")));
    }

    // -------------------------------------------------------------------------
    // ACTIVATE / DEACTIVATE (a doctor who left stays in history; status lives on the staff row)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> updateDoctorStatus(Long id, ActiveStatusRequest request) {
        Doctor doctor = findDetail(id);
        doctor.getStaff().setStatus(request.active() ? "ACTIVE" : "INACTIVE");
        staffRepository.save(doctor.getStaff());
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                DoctorResponse.from(doctor),
                new Status(200, messageUtils.getMessage("doctor.status.success")));
    }

    // -------------------------------------------------------------------------
    // DELETE (mistaken entries only; schedules go with it via ON DELETE CASCADE)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> deleteDoctor(Long id) {
        Doctor doctor = findDetail(id);
        Staff staff = doctor.getStaff();
        // Order matters: doctors.staff_id references staff.
        doctorRepository.delete(doctor);
        staffRepository.delete(staff);
        // Appointments (later) will reference doctors; flush so that failure surfaces here and rolls back both rows.
        doctorRepository.flush();
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                null,
                new Status(200, messageUtils.getMessage("doctor.delete.success")));
    }

    private Doctor findDetail(Long id) {
        return doctorRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("doctor.notfound")));
    }

    /** Doctors must belong to a department. */
    private Department resolveDepartment(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("department.notfound")));
    }
}
