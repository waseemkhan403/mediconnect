package com.mediconnect.dto.chat;

import java.util.List;

/**
 * One page of a conversation, oldest first (ready to render). hasMore: older messages exist; fetch them with
 * ?before={the first message's id}.
 */
public record ChatMessagesResponse(List<ChatMessageResponse> messages, boolean hasMore) {
}
