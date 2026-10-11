package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A medicine the hospital pharmacy stocks (the catalogue). Stock itself lives in batches (MedicineBatch), because
 * every batch has its own expiry date and price. Name + form + strength is unique ignoring case (index in
 * schema.sql), so "Paracetamol TABLET 500 mg" and "Paracetamol SYRUP 120 mg/5 ml" are different medicines.
 * Deactivate instead of deleting once it has been stocked or prescribed (the foreign keys refuse the delete).
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "medicines", check = {
        @CheckConstraint(name = "ck_medicines_form", constraint =
                "form IN ('TABLET', 'CAPSULE', 'SYRUP', 'SUSPENSION', 'INJECTION', 'OINTMENT', 'CREAM', 'DROPS', "
                        + "'INHALER', 'POWDER', 'OTHER')"),
        @CheckConstraint(name = "ck_medicines_reorder", constraint = "reorder_level >= 0")
})
public class Medicine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "medicine_id")
    private Long id;

    /** Brand or common name, e.g. "Dolo 650". */
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** Active ingredient, e.g. "Paracetamol". */
    @Column(name = "generic_name", length = 150)
    private String genericName;

    @Column(name = "form", nullable = false, length = 20)
    private String form;

    /** e.g. "650 mg", "5 mg/ml". */
    @Column(name = "strength", length = 50)
    private String strength;

    @Column(name = "manufacturer", length = 150)
    private String manufacturer;

    /** Usable stock at or below this level is reported as low. */
    @Column(name = "reorder_level", nullable = false)
    @Builder.Default
    private int reorderLevel = 0;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
