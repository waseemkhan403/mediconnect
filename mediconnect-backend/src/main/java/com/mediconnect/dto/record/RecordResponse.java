package com.mediconnect.dto.record;

import com.mediconnect.entity.MedicalRecord;
import com.mediconnect.entity.User;

import java.time.Instant;
import java.time.LocalDate;

/** Record metadata (never the file; that comes from a download link). Used for lists too. */
public record RecordResponse(
        Long id,
        Long patientId,
        String patientUhid,
        String patientName,
        String title,
        String recordType,
        LocalDate recordDate,
        String notes,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Long uploadedByUserId,
        String uploadedByRole,
        Instant uploadedAt,
        String status,
        String retractionReason
) {
    /** Expects patient and uploadedBy (+ staff) to be loaded. */
    public static RecordResponse from(MedicalRecord r) {
        User u = r.getUploadedBy();
        return new RecordResponse(
                r.getId(), r.getPatient().getId(), r.getPatient().getUhid(), r.getPatient().getFullName(),
                r.getTitle(), r.getRecordType(), r.getRecordDate(), r.getNotes(),
                r.getOriginalFilename(), r.getContentType(), r.getSizeBytes(),
                u != null ? u.getId() : null,
                u != null ? u.role() : null,
                r.getUploadedAt(), r.getStatus(), r.getRetractionReason());
    }
}
