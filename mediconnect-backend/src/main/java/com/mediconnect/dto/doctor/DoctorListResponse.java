package com.mediconnect.dto.doctor;

import com.mediconnect.entity.Doctor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Card/row for listings and public search. Deliberately has NO phone, email, date of birth or
 * registration details, so it is safe to expose to patients.
 */
public record DoctorListResponse(
        Long id,
        String fullName,
        String specialization,
        String qualifications,
        String departmentName,
        Integer experienceYears,
        BigDecimal consultationFee,
        List<String> languages,
        boolean verified
) {
    public static DoctorListResponse from(Doctor d) {
        var dep = d.getStaff().getDepartment();
        return new DoctorListResponse(
                d.getId(), d.getStaff().getFullName(), d.getSpecialization(), d.getQualifications(),
                dep != null ? dep.getName() : null,
                d.getExperienceYears(), d.getConsultationFee(),
                List.copyOf(d.getLanguages()), d.isVerified());
    }
}
