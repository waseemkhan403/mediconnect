package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * One medicine on an encounter's prescription. medicineName is always filled (free text); medicine optionally links
 * it to the pharmacy catalogue, which saves the pharmacist matching the name by hand.
 * frequency uses the usual Indian notation, e.g. "1-0-1" (morning-noon-night), or words like "twice daily".
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "prescription_items", check = {
        @CheckConstraint(name = "ck_prescription_duration", constraint = "duration_days IS NULL OR duration_days > 0")
})
public class PrescriptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prescription_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false)
    private Encounter encounter;

    @Column(name = "medicine_name", nullable = false, length = 200)
    private String medicineName;

    /** Catalogue entry, when the doctor picked one. A medicine that is referenced cannot be deleted (deactivate it). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medicine_id")
    private Medicine medicine;

    /** e.g. "500 mg", "10 ml". */
    @Column(name = "dosage", length = 100)
    private String dosage;

    @Column(name = "frequency", length = 50)
    private String frequency;

    @Column(name = "duration_days")
    private Integer durationDays;

    /** e.g. "after food". */
    @Column(name = "instructions", length = 300)
    private String instructions;
}
