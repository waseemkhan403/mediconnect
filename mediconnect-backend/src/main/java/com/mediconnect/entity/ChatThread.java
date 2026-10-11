package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * The conversation between a patient and the doctor after one consultation. Opened automatically when the
 * encounter is finalized and open for a fixed window (app.chat.window-days) after that; the doctor may close it
 * earlier, and so may an admin (safety). Once closed it is read-only for good: follow-up questions after that need a new appointment.
 * Open = closedAt is null and closesAt is in the future (computed, never stored, so it cannot go stale).
 * Read state is kept per side as the id of the last message read, so unread = messages with a higher id.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "chat_threads", check = {
        @CheckConstraint(name = "ck_chat_threads_window", constraint = "closes_at > opened_at")
})
public class ChatThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_thread_id")
    private Long id;

    /** One thread per encounter. No ON DELETE: only DRAFT encounters can be deleted, and they have no thread. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false, unique = true)
    private Encounter encounter;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closes_at", nullable = false)
    private Instant closesAt;

    /** Set when the doctor (or an admin) ends the conversation before closesAt. */
    @Column(name = "closed_at")
    private Instant closedAt;

    /** Who closed it early: DOCTOR or ADMIN (null while open or when the window simply ran out). */
    @Column(name = "closed_by_role", length = 10)
    private String closedByRole;

    /** Last message (or the opening time), so the inbox can be sorted by recent activity. */
    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "patient_last_read_id", nullable = false)
    @Builder.Default
    private long patientLastReadId = 0;

    @Column(name = "doctor_last_read_id", nullable = false)
    @Builder.Default
    private long doctorLastReadId = 0;

    public boolean isOpen(Instant now) {
        return closedAt == null && closesAt.isAfter(now);
    }
}
