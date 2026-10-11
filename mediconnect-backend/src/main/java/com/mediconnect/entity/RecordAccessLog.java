package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * One read of a medical record: who opened it (VIEW) or downloaded the file (DOWNLOAD), and when.
 * Insert-only. Patients can read the log of their own records, so they can see who looked at them.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "record_access_logs", check = {
        @CheckConstraint(name = "ck_access_action", constraint = "action IN ('VIEW', 'DOWNLOAD')")
})
public class RecordAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "access_log_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    private MedicalRecord record;

    /** Kept even if the login is later deleted (then null): the read still happened. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User user;

    @Column(name = "action", nullable = false, length = 10)
    private String action;

    @Column(name = "accessed_at", nullable = false)
    private Instant accessedAt;
}
