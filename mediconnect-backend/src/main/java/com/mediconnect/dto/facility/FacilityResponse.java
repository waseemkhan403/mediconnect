package com.mediconnect.dto.facility;

import com.mediconnect.entity.Facility;

public record FacilityResponse(Long id, String name, String description, boolean active) {

    public static FacilityResponse from(Facility f) {
        return new FacilityResponse(f.getId(), f.getName(), f.getDescription(), f.isActive());
    }
}
