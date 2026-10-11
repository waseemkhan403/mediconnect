package com.mediconnect.dto.chat;

import com.mediconnect.entity.ChatMessage;

import java.time.Instant;

/** Also the WebSocket payload, which is why it carries threadId. */
public record ChatMessageResponse(
        Long id,
        Long threadId,
        String senderRole,
        String senderName,
        String body,
        Instant sentAt
) {
    /** senderName: the patient's or the doctor's name, taken from the thread (no extra query per message). */
    public static ChatMessageResponse from(ChatMessage m, String patientName, String doctorName) {
        return new ChatMessageResponse(
                m.getId(), m.getThread().getId(), m.getSenderRole(),
                "DOCTOR".equals(m.getSenderRole()) ? doctorName : patientName,
                m.getBody(), m.getSentAt());
    }
}
