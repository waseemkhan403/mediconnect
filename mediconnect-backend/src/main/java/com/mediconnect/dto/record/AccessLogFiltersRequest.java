package com.mediconnect.dto.record;

/** All fields optional. A patient always gets the log of their own records only. */
public record AccessLogFiltersRequest(Long recordId, Long patientId) {
}
