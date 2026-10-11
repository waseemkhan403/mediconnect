package com.mediconnect.dto.chat;

/** Optional. Patients and doctors always get the log of their own chats only. */
public record ChatAccessLogFiltersRequest(Long threadId) {
}
