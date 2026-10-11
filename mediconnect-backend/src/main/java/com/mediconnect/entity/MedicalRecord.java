package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A document in a patient's record (lab report, scan, discharge summary...). Append-only: never edited or deleted.
 * A wrong upload is RETRACTED (hidden from lists, reason kept, file kept), so the history stays complete.
 * The file itself lives in FileStorage under storageKey (a random id, never the user's file name).
 * recordType: LAB_REPORT | IMAGING | PRESCRIPTION | DISCHARGE_SUMMARY | OTHER.  status: ACTIVE | RETRACTED.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "medical_records", check = {
        @CheckConstraint(name = "ck_records_type", constraint = "record_type IN ('LAB_REPORT', 'IMAGING', "
                + "'PRESCRIPTION', 'DISCHARGE_SUMMARY', 'OTHER')"),
        @CheckConstraint(name = "ck_records_status", constraint = "status IN ('ACTIVE', 'RETRACTED')"),
        @CheckConstraint(name = "ck_records_retracted",
                constraint = "status <> 'RETRACTED' OR retraction_reason IS NOT NULL")
})
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_id")
    private Long id;

    /** No ON DELETE: a patient with records cannot be deleted. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "record_type", nullable = false, length = 30)
    private String recordType;

    /** Date on the document (e.g. when the test was done), not the upload date. */
    @Column(name = "record_date")
    private LocalDate recordDate;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "storage_key", nullable = false, unique = true, length = 64)
    private String storageKey;

    /** Who added it. A deleted login leaves the record in place. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User uploadedBy;

    /** Part of the record: when it entered the patient's file. */
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "retraction_reason", length = 500)
    private String retractionReason;
}
