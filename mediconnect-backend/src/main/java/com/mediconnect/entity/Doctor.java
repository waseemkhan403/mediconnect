package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Professional data of a doctor. Name, contact and department live on the linked Staff row.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "doctors",
        uniqueConstraints = @UniqueConstraint(name = "uq_doctors_registration",
                columnNames = {"registration_council", "registration_number"}),
        check = {
                @CheckConstraint(name = "ck_doctors_experience", constraint = "experience_years >= 0"),
                @CheckConstraint(name = "ck_doctors_fee", constraint = "consultation_fee >= 0")
        })
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "doctor_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false, unique = true)
    private Staff staff;

    /** Free text, e.g. "Interventional Cardiology". Keep spelling consistent (use a fixed dropdown in the UI). */
    @Column(name = "specialization", nullable = false, length = 100)
    private String specialization;

    /** Display text, e.g. "MBBS, MD (Cardiology), DM (Cardiology)". Not queryable per degree. */
    @Column(name = "qualifications", length = 500)
    private String qualifications;

    @Column(name = "registration_number", nullable = false, length = 50)
    private String registrationNumber;

    /** Issuing medical council. Unique together with the number. */
    @Column(name = "registration_council", nullable = false, length = 150)
    private String registrationCouncil;

    @Column(name = "experience_years", nullable = false)
    @Builder.Default
    private Integer experienceYears = 0;

    @Column(name = "consultation_fee", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal consultationFee = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "languages", nullable = false, columnDefinition = "text[]")
    @Builder.Default
    private List<String> languages = new ArrayList<>();

    @Column(name = "bio", columnDefinition = "text")
    private String bio;

    /** Set by an admin after checking the registration number. Only verified doctors should be bookable. */
    @Column(name = "verified", nullable = false)
    @Builder.Default
    private boolean verified = false;
}
