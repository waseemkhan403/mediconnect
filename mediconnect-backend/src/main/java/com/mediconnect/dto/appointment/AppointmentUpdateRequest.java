package com.mediconnect.dto.appointment;

import jakarta.validation.constraints.Size;

/**
 * PUT /appointments/{id}: replaces the editable details only. Patient and doctor never change on a booking
 * (cancel and book again), the time changes through /reschedule, and the status through /status.
 * reason left out becomes null.
 */
public record AppointmentUpdateRequest(

        @Size(max = 500, message = "reason must not exceed 500 characters")
        String reason

) {
}
