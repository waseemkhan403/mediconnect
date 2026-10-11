package com.mediconnect.dto.record;

/**
 * All fields optional. includeRetracted (default false) also shows withdrawn uploads.
 * A doctor sees a patient's records by passing patientId while that patient's consent is active; without it,
 * staff only see what they uploaded themselves.
 */
public record RecordFiltersRequest(
        Long patientId,
        String recordType,
        Boolean includeRetracted
) {
}
