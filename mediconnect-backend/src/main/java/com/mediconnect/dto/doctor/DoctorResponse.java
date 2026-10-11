package com.mediconnect.dto.doctor;

import com.mediconnect.entity.Department;
import com.mediconnect.entity.Doctor;
import com.mediconnect.entity.Staff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Full detail for admin/doctor screens. Contains contact details, so do not use it on public pages. */
public record DoctorResponse(
        Long id,
        Long staffId,
        String staffCode,
        String fullName,
        String gender,
        LocalDate dateOfBirth,
        String phone,
        String email,
        String designation,
        Long departmentId,
        String departmentName,
        LocalDate joiningDate,
        String status,
        String specialization,
        String qualifications,
        String registrationNumber,
        String registrationCouncil,
        Integer experienceYears,
        BigDecimal consultationFee,
        List<String> languages,
        String bio,
        boolean verified
) {
    /** Expects staff and staff.department to be loaded (use DoctorRepository.findDetailById). */
    public static DoctorResponse from(Doctor d) {
        Staff s = d.getStaff();
        Department dep = s.getDepartment();
        return new DoctorResponse(
                d.getId(), s.getId(), s.getStaffCode(), s.getFullName(), s.getGender(), s.getDateOfBirth(),
                s.getPhone(), s.getEmail(), s.getDesignation(),
                dep != null ? dep.getId() : null,
                dep != null ? dep.getName() : null,
                s.getJoiningDate(), s.getStatus(),
                d.getSpecialization(), d.getQualifications(),
                d.getRegistrationNumber(), d.getRegistrationCouncil(),
                d.getExperienceYears(), d.getConsultationFee(),
                List.copyOf(d.getLanguages()), d.getBio(), d.isVerified());
    }
}
