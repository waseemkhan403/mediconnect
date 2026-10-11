package com.mediconnect.helper;

import com.mediconnect.repository.HospitalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

/**
 * The hospital's timezone. Schedules are stored as local times, appointments as UTC instants;
 * this zone converts between the two.
 */
@Component
@RequiredArgsConstructor
public class HospitalZone {

    /** Used before the hospital record is created (same default as the hospital's timezone column). */
    public static final ZoneId DEFAULT = ZoneId.of("Asia/Kolkata");

    private final HospitalRepository hospitalRepository;

    public ZoneId get() {
        return hospitalRepository.findSingleton()
                .map(h -> ZoneId.of(h.getTimezone()))
                .orElse(DEFAULT);
    }
}
