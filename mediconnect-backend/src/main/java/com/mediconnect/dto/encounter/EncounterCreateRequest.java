package com.mediconnect.dto.encounter;

import jakarta.validation.constraints.NotNull;

/** Open the encounter for a checked-in (or in-consultation) appointment. Everything else is filled in later. */
public record EncounterCreateRequest(

        @NotNull(message = "appointmentId is required")
        Long appointmentId

) {
}
