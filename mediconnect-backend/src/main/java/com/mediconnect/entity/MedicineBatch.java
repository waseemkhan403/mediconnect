package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One delivery of a medicine: batch number, expiry date, price per unit (MRP) and how much is left.
 * quantityOnHand only changes by dispensing (minus) and stock adjustments (either way). The CHECK keeps it from
 * going negative even if two pharmacists dispense at once (the service also locks the rows it takes from).
 * A batch is usable while expiryDate is today or later (hospital date).
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "medicine_batches",
        uniqueConstraints = @UniqueConstraint(name = "uq_batches_medicine_number",
                columnNames = {"medicine_id", "batch_number"}),
        check = {
                @CheckConstraint(name = "ck_batches_received", constraint = "quantity_received > 0"),
                @CheckConstraint(name = "ck_batches_on_hand", constraint = "quantity_on_hand >= 0"),
                @CheckConstraint(name = "ck_batches_price", constraint = "unit_price >= 0")
        })
public class MedicineBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "batch_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicine_id", nullable = false)
    private Medicine medicine;

    @Column(name = "batch_number", nullable = false, length = 50)
    private String batchNumber;

    /** Last day the batch may be used. */
    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "quantity_received", nullable = false)
    private int quantityReceived;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    /** Selling price per unit (tablet, bottle...). Copied onto each dispensed item, so later changes do not rewrite bills. */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by_user_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User receivedBy;
}
