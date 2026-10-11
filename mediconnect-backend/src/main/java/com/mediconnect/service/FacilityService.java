package com.mediconnect.service;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.dto.facility.FacilityFiltersRequest;
import com.mediconnect.dto.facility.FacilityRequest;
import com.mediconnect.dto.facility.FacilityResponse;
import com.mediconnect.entity.Facility;
import com.mediconnect.exception.AlreadyExistException;
import com.mediconnect.exception.NotFoundException;
import com.mediconnect.repository.FacilityRepository;
import com.mediconnect.response.PageResponse;
import com.mediconnect.response.ResponseHandler;
import com.mediconnect.response.Status;
import com.mediconnect.util.Texts;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FacilityService {

    private final FacilityRepository facilityRepository;
    private final MessageUtils messageUtils;

    @Transactional(readOnly = true)
    public ResponseEntity<Object> getFacilities(FacilityFiltersRequest filters, Pageable pageable) {
        if (filters == null) {
            filters = new FacilityFiltersRequest(null, null);
        }
        Page<FacilityResponse> page = facilityRepository
                .findAllWithFilters(Texts.clean(filters.name()), filters.active(), pageable)
                .map(FacilityResponse::from);
        return ResponseHandler.generatePageResponse(
                HttpStatus.OK,
                PageResponse.from(page),
                new Status(200, messageUtils.getMessage("facility.list.success")));
    }

    /** Active facilities for the public hospital page (not paged). */
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getActiveFacilities() {
        List<FacilityResponse> facilities = facilityRepository.findByActiveTrueOrderByNameAsc()
                .stream().map(FacilityResponse::from).toList();
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                facilities,
                new Status(200, messageUtils.getMessage("facility.list.success")));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Object> getFacilityById(Long id) {
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                FacilityResponse.from(find(id)),
                new Status(200, messageUtils.getMessage("facility.get.success", String.valueOf(id))));
    }

    @Transactional
    public ResponseEntity<Object> addFacility(FacilityRequest request) {
        String name = request.name().trim();
        if (facilityRepository.existsByNameIgnoreCase(name)) {
            throw new AlreadyExistException(messageUtils.getMessage("facility.exists", name));
        }
        Facility saved = facilityRepository.save(Facility.builder()
                .name(name)
                .description(Texts.clean(request.description()))
                .active(request.active() == null || request.active())
                .build());
        return ResponseHandler.generateResponse(
                HttpStatus.CREATED,
                FacilityResponse.from(saved),
                new Status(201, messageUtils.getMessage("facility.add.success")));
    }

    @Transactional
    public ResponseEntity<Object> updateFacility(Long id, FacilityRequest request) {
        Facility existing = find(id);
        String name = request.name().trim();
        if (facilityRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new AlreadyExistException(messageUtils.getMessage("facility.exists", name));
        }
        existing.setName(name);
        existing.setDescription(Texts.clean(request.description()));
        if (request.active() != null) {
            existing.setActive(request.active());
        }
        Facility saved = facilityRepository.save(existing);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                FacilityResponse.from(saved),
                new Status(200, messageUtils.getMessage("facility.update.success")));
    }

    @Transactional
    public ResponseEntity<Object> deleteFacility(Long id) {
        Facility facility = find(id);
        facilityRepository.delete(facility);
        facilityRepository.flush();
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                null,
                new Status(200, messageUtils.getMessage("facility.delete.success")));
    }

    private Facility find(Long id) {
        return facilityRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("facility.notfound")));
    }
}
