package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * An admin looking into a chat they are not part of: READ (opened its messages) or CLOSE (ended it).
 * Insert-only. The patient and the doctor of the chat can read this log, so oversight is never invisible.
 * The two parties' own reads are not logged: it is their conversation.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "chat_access_logs", check = {
        @CheckConstraint(name = "ck_chat_access_action", constraint = "action IN ('READ', 'CLOSE')")
})
public class ChatAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_access_log_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_thread_id", nullable = false)
    private ChatThread thread;

    /** Kept even if the login is later deleted (then null): the access still happened. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User user;

    @Column(name = "action", nullable = false, length = 10)
    private String action;

    @Column(name = "accessed_at", nullable = false)
    private Instant accessedAt;
}
