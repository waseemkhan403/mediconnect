package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * One message in a chat thread. Never edited or deleted: a teleconsultation chat is part of the medical record
 * (Telemedicine Practice Guidelines ask doctors to keep it).
 * senderRole (PATIENT or DOCTOR) says which side wrote it, so the message stays attributable even if the login
 * is deleted later.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "chat_messages",
        indexes = @Index(name = "ix_chat_messages_thread", columnList = "chat_thread_id, chat_message_id"),
        check = @CheckConstraint(name = "ck_chat_messages_role", constraint = "sender_role IN ('PATIENT', 'DOCTOR')"))
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_message_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_thread_id", nullable = false)
    private ChatThread thread;

    /** The login that typed it; null if that login was deleted later. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_user_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User sender;

    @Column(name = "sender_role", nullable = false, length = 10)
    private String senderRole;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;
}
