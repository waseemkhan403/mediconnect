package com.mediconnect.dto.record;

import com.mediconnect.dto.ValidationRegex;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Form fields sent with the file (multipart/form-data, the file in a part called "file").
 * recordDate: the date on the document (e.g. when the test was done); optional.
 */
public record RecordUploadRequest(

        @NotNull(message = "patientId is required")
        Long patientId,

        @NotBlank(message = "title is required")
        @Size(max = 200, message = "title must not exceed 200 characters")
        String title,

        @NotBlank(message = "recordType is required")
        @Pattern(regexp = ValidationRegex.RECORD_TYPE,
                message = "recordType must be LAB_REPORT, IMAGING, PRESCRIPTION, DISCHARGE_SUMMARY or OTHER")
        String recordType,

        @PastOrPresent(message = "recordDate must not be in the future")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate recordDate,

        @Size(max = 1000, message = "notes must not exceed 1000 characters")
        String notes

) {
}
