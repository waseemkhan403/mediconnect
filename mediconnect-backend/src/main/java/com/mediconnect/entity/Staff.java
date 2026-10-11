package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Every employee, doctors included. Holds person-level data (name, contact) so that leave,
 * shifts and attendance can be built once for everyone. A doctor is a Staff row plus a Doctor row.
 * Allowed values are guarded by CHECK constraints (below):
 *   gender: MALE | FEMALE | OTHER
 *   staffType: DOCTOR | NURSE | RECEPTIONIST | PHARMACIST | LAB_TECHNICIAN | ADMIN | OTHER
 *   status: ACTIVE | INACTIVE   ("on leave" is derived from leave records, not stored here)
 * Email is unique ignoring case through an index in schema.sql.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "staff", check = {
        @CheckConstraint(name = "ck_staff_gender", constraint = "gender IN ('MALE', 'FEMALE', 'OTHER')"),
        @CheckConstraint(name = "ck_staff_type", constraint = "staff_type IN ('DOCTOR', 'NURSE', 'RECEPTIONIST', "
                + "'PHARMACIST', 'LAB_TECHNICIAN', 'ADMIN', 'OTHER')"),
        @CheckConstraint(name = "ck_staff_status", constraint = "status IN ('ACTIVE', 'INACTIVE')")
})
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "staff_id")
    private Long id;

    @Column(name = "staff_code", nullable = false, unique = true, length = 20)
    private String staffCode;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "gender", nullable = false, length = 10)
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "staff_type", nullable = false, length = 30)
    private String staffType;

    /** Display title, e.g. "Senior Consultant", "Head Nurse". */
    @Column(name = "designation", length = 100)
    private String designation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";
}
