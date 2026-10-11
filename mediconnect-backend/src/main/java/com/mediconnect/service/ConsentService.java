package com.mediconnect.service;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.dto.consent.ConsentFiltersRequest;
import com.mediconnect.dto.consent.ConsentRequest;
import com.mediconnect.dto.consent.ConsentResponse;
import com.mediconnect.entity.Doctor;
import com.mediconnect.entity.Patient;
import com.mediconnect.entity.RecordConsent;
import com.mediconnect.exception.AlreadyExistException;
import com.mediconnect.exception.BadRequestException;
import com.mediconnect.exception.NotFoundException;
import com.mediconnect.repository.DoctorRepository;
import com.mediconnect.repository.PatientRepository;
import com.mediconnect.repository.RecordConsentRepository;
import com.mediconnect.response.PageResponse;
import com.mediconnect.response.ResponseHandler;
import com.mediconnect.response.Status;
import com.mediconnect.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Patient consent for one doctor to read their uploaded records and their FINAL encounters with other doctors.
 * Granted and revoked by the patient login that owns the record, or by reception/admin on the patient's behalf.
 * Grants are never deleted: revoking stamps revokedAt, so the history of who was allowed what remains.
 */
@Service
@RequiredArgsConstructor
public class ConsentService {

    static final int DEFAULT_DAYS = 30;

    private final RecordConsentRepository consentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final CurrentUser currentUser;
    private final MessageUtils messageUtils;

    /** Used by records and encounters: may this doctor read this patient's shared data right now? */
    @Transactional(readOnly = true)
    public boolean hasActiveConsent(Long patientId, Long doctorId) {
        return patientId != null && doctorId != null
                && consentRepository.existsActive(patientId, doctorId, Instant.now());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Object> getConsents(ConsentFiltersRequest filters, Pageable pageable) {
        if (filters == null) {
            filters = new ConsentFiltersRequest(null, null, null);
        }
        Instant now = Instant.now();
        // Forced server-side: a patient sees grants on their own records, a doctor the grants given to them.
        Long ownerId = currentUser.isPatient() ? currentUser.id() : null;
        Long doctorId = currentUser.isDoctor() ? currentUser.doctorId() : filters.doctorId();
        Page<ConsentResponse> page = consentRepository.findAllWithFilters(
                        filters.patientId(), doctorId, ownerId, Boolean.TRUE.equals(filters.activeOnly()), now, pageable)
                .map(c -> ConsentResponse.from(c, now));
        return ResponseHandler.generatePageResponse(
                HttpStatus.OK,
                PageResponse.from(page),
                new Status(200, messageUtils.getMessage("consent.list.success")));
    }

    @Transactional
    public ResponseEntity<Object> addConsent(ConsentRequest request) {
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("patient.notfound")));
        if (currentUser.isPatient() && !ownedByCaller(patient)) {
            throw new NotFoundException(messageUtils.getMessage("patient.notfound"));
        }
        Doctor doctor = doctorRepository.findDetailById(request.doctorId())
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("doctor.notfound")));
        Instant now = Instant.now();
        if (consentRepository.existsActive(patient.getId(), doctor.getId(), now)) {
            throw new AlreadyExistException(messageUtils.getMessage("consent.exists"));
        }
        int days = request.days() != null ? request.days() : DEFAULT_DAYS;
        RecordConsent saved = consentRepository.save(RecordConsent.builder()
                .patient(patient)
                .doctor(doctor)
                .grantedAt(now)
                .expiresAt(now.plus(Duration.ofDays(days)))
                .build());
        return ResponseHandler.generateResponse(
                HttpStatus.CREATED,
                ConsentResponse.from(saved, now),
                new Status(201, messageUtils.getMessage("consent.add.success", doctor.getStaff().getFullName())));
    }

    @Transactional
    public ResponseEntity<Object> revokeConsent(Long id) {
        RecordConsent consent = consentRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("consent.notfound")));
        if (currentUser.isPatient() && !ownedByCaller(consent.getPatient())) {
            throw new NotFoundException(messageUtils.getMessage("consent.notfound"));
        }
        Instant now = Instant.now();
        if (!consent.isActive(now)) {
            throw new BadRequestException(messageUtils.getMessage("consent.inactive"));
        }
        consent.setRevokedAt(now);
        RecordConsent saved = consentRepository.save(consent);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                ConsentResponse.from(saved, now),
                new Status(200, messageUtils.getMessage("consent.revoke.success")));
    }

    private boolean ownedByCaller(Patient patient) {
        return patient.getOwner() != null && patient.getOwner().getId().equals(currentUser.id());
    }
}
