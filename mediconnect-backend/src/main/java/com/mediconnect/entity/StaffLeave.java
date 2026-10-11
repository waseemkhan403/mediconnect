package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;

/**
 * Leave of one staff member over whole days (startDate..endDate, both inclusive, hospital-local dates).
 * Lives on staff, not doctors, so it works for every employee; for doctors an APPROVED leave also removes
 * bookable slots on those days.
 * leaveType: CASUAL | SICK | EARNED | OTHER.  status: PENDING | APPROVED | REJECTED | CANCELLED.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "staff_leaves", check = {
        @CheckConstraint(name = "ck_leaves_type", constraint = "leave_type IN ('CASUAL', 'SICK', 'EARNED', 'OTHER')"),
        @CheckConstraint(name = "ck_leaves_status",
                constraint = "status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')"),
        @CheckConstraint(name = "ck_leaves_dates", constraint = "end_date >= start_date")
})
public class StaffLeave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "leave_id")
    private Long id;

    /** Leave records go with a (mistakenly created) staff row when it is deleted. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Staff staff;

    @Column(name = "leave_type", nullable = false, length = 20)
    private String leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** The admin's comment when approving, rejecting or cancelling. */
    @Column(name = "review_note", length = 500)
    private String reviewNote;
}
