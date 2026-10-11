package com.mediconnect.dto.encounter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * PUT /encounters/{id}: the doctor's clinical record, replaced as a whole while DRAFT (fields left out become null,
 * a missing prescriptions list means no medicines). Vitals are not here: they have their own endpoint so a nurse's
 * readings are never wiped by the doctor saving notes. diagnosis is required only when finalizing.
 */
public record EncounterRequest(

        @Size(max = 1000, message = "chiefComplaint must not exceed 1000 characters")
        String chiefComplaint,

        @Size(max = 20000, message = "clinicalNotes must not exceed 20000 characters")
        String clinicalNotes,

        @Size(max = 1000, message = "diagnosis must not exceed 1000 characters")
        String diagnosis,

        @Size(max = 2000, message = "advice must not exceed 2000 characters")
        String advice,

        LocalDate followUpDate,

        @Size(max = 30, message = "prescriptions must not exceed 30 medicines")
        List<@Valid PrescriptionItemRequest> prescriptions

) {
}
