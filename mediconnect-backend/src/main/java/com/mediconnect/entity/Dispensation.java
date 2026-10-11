package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Medicines handed over at the pharmacy counter for one FINAL encounter's prescription. An encounter can have
 * several (part now, the rest when stock arrives). Permanent: never edited or deleted; a returned medicine goes
 * back into stock with a RETURNED stock adjustment.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "dispensations", check = {
        @CheckConstraint(name = "ck_dispensations_total", constraint = "total_amount >= 0")
})
public class Dispensation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dispensation_id")
    private Long id;

    /** No ON DELETE: FINAL encounters are never deleted anyway. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false)
    private Encounter encounter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispensed_by_user_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User dispensedBy;

    @Column(name = "dispensed_at", nullable = false)
    private Instant dispensedAt;

    /** Sum of the items' line totals, stored because prices are a fact of the sale. */
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "notes", length = 500)
    private String notes;

    @OneToMany(mappedBy = "dispensation", cascade = CascadeType.ALL)
    @OrderBy("id")
    @Builder.Default
    private List<DispensationItem> items = new ArrayList<>();
}
