package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * A manual stock change on a batch (damaged, expired and thrown away, returned by a patient, counting error).
 * Insert-only: together with the received quantity and the dispensed items it explains every unit of stock.
 * quantityAfter is the batch's quantity on hand right after this change.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "stock_adjustments", check = {
        @CheckConstraint(name = "ck_adjustments_change", constraint = "quantity_change <> 0"),
        @CheckConstraint(name = "ck_adjustments_after", constraint = "quantity_after >= 0"),
        @CheckConstraint(name = "ck_adjustments_reason",
                constraint = "reason IN ('DAMAGED', 'EXPIRED', 'RETURNED', 'CORRECTION', 'OTHER')")
})
public class StockAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "adjustment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private MedicineBatch batch;

    /** Negative removes stock, positive adds it. */
    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Column(name = "quantity_after", nullable = false)
    private int quantityAfter;

    @Column(name = "reason", nullable = false, length = 20)
    private String reason;

    @Column(name = "note", length = 300)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjusted_by_user_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User adjustedBy;

    @Column(name = "adjusted_at", nullable = false)
    private Instant adjustedAt;
}
