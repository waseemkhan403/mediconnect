package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * What happened in one consultation: vitals, notes, diagnosis, advice and the prescription. One per appointment
 * (the appointment is the booking, this is the clinical record).
 * status: DRAFT (editable) -> FINAL (locked by the doctor). A FINAL encounter is never edited or deleted;
 * corrections are appended as addenda, so the record shows what was known at the time.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "encounters", check = {
        @CheckConstraint(name = "ck_encounters_status", constraint = "status IN ('DRAFT', 'FINAL')"),
        @CheckConstraint(name = "ck_encounters_final",
                constraint = "status <> 'FINAL' OR (finalized_at IS NOT NULL AND diagnosis IS NOT NULL)")
})
public class Encounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "encounter_id")
    private Long id;

    /** No ON DELETE: an appointment with a clinical record cannot be deleted. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    // ---- vitals (recorded by a nurse or the doctor; all optional) ----
    @Column(name = "bp_systolic")
    private Integer bpSystolic;

    @Column(name = "bp_diastolic")
    private Integer bpDiastolic;

    @Column(name = "pulse_rate")
    private Integer pulseRate;

    @Column(name = "temperature_c", precision = 4, scale = 1)
    private BigDecimal temperatureC;

    @Column(name = "spo2")
    private Integer spo2;

    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "height_cm")
    private Integer heightCm;

    // ---- clinical (doctor only) ----
    @Column(name = "chief_complaint", length = 1000)
    private String chiefComplaint;

    @Column(name = "clinical_notes", columnDefinition = "text")
    private String clinicalNotes;

    @Column(name = "diagnosis", length = 1000)
    private String diagnosis;

    @Column(name = "advice", length = 2000)
    private String advice;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    /** When the doctor signed off. Part of the medical record, not an audit column. */
    @Column(name = "finalized_at")
    private Instant finalizedAt;

    /** The prescription. Replaced as a whole while DRAFT; orphanRemoval deletes items dropped from the list. */
    @OneToMany(mappedBy = "encounter", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @Builder.Default
    private List<PrescriptionItem> prescriptionItems = new ArrayList<>();

    @OneToMany(mappedBy = "encounter", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("addedAt")
    @Builder.Default
    private List<EncounterAddendum> addenda = new ArrayList<>();
}
