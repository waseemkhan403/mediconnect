package com.mediconnect.dto.chat;

/**
 * All fields optional. openOnly (default false) hides closed threads.
 * Patients always get the threads of patients they own; doctors their own threads (doctorId is ignored for both).
 * Admins see every thread and may filter by doctorId.
 */
public record ChatThreadFiltersRequest(Long encounterId, Long patientId, Long doctorId, Boolean openOnly) {
}
