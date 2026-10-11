package com.mediconnect.service;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.dto.ActiveStatusRequest;
import com.mediconnect.dto.patient.PatientFiltersRequest;
import com.mediconnect.dto.patient.PatientListResponse;
import com.mediconnect.dto.patient.PatientRequest;
import com.mediconnect.dto.patient.PatientResponse;
import com.mediconnect.entity.Patient;
import com.mediconnect.exception.AlreadyExistException;
import com.mediconnect.exception.NotFoundException;
import com.mediconnect.repository.AppointmentRepository;
import com.mediconnect.repository.PatientRepository;
import com.mediconnect.repository.UserRepository;
import com.mediconnect.response.PageResponse;
import com.mediconnect.response.ResponseHandler;
import com.mediconnect.response.Status;
import com.mediconnect.security.CurrentUser;
import com.mediconnect.util.Texts;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full CRUD. DELETE removes a patient permanently; once appointments or medical records reference a
 * patient, the database refuses the delete (409). Use updatePatientStatus to deactivate instead.
 * Record access: a patient login only reaches the records it owns (and owns what it registers);
 * a doctor only patients they have an appointment with. Anything else is "not found".
 */
@Service
@RequiredArgsConstructor
public class PatientService {

    private static final String UHID_PREFIX = "UH";

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final MessageUtils messageUtils;

    @Transactional(readOnly = true)
    public ResponseEntity<Object> getPatients(PatientFiltersRequest filters, Pageable pageable) {
        if (filters == null) {
            filters = new PatientFiltersRequest(null, null, null, null);
        }
        Page<PatientListResponse> page = patientRepository.findAllWithFilters(
                        Texts.clean(filters.name()),
                        Texts.phone(filters.phone()),   // same normalisation as stored phones
                        Texts.clean(filters.uhid()),
                        filters.active(),
                        currentUser.isPatient() ? currentUser.id() : null,
                        pageable)
                .map(PatientListResponse::from);
        return ResponseHandler.generatePageResponse(
                HttpStatus.OK,
                PageResponse.from(page),
                new Status(200, messageUtils.getMessage("patient.list.success")));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Object> getPatientById(Long id) {
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                PatientResponse.from(findAccessible(id)),
                new Status(200, messageUtils.getMessage("patient.get.success", String.valueOf(id))));
    }

    @Transactional
    public ResponseEntity<Object> addPatient(PatientRequest request) {
        String name = request.fullName().trim();
        String phone = Texts.phone(request.phone());

        if (patientRepository.existsByFullNameIgnoreCaseAndDateOfBirthAndPhone(name, request.dateOfBirth(), phone)) {
            throw new AlreadyExistException(messageUtils.getMessage("patient.exists"));
        }

        Patient patient = Patient.builder()
                .uhid(UHID_PREFIX + String.format("%08d", patientRepository.nextUhidSequence()))
                .fullName(name)
                .dateOfBirth(request.dateOfBirth())
                .gender(request.gender())
                .phone(phone)
                .email(Texts.email(request.email()))
                .bloodGroup(request.bloodGroup())
                .addressLine1(Texts.clean(request.addressLine1()))
                .addressLine2(Texts.clean(request.addressLine2()))
                .city(Texts.clean(request.city()))
                .state(Texts.clean(request.state()))
                .postalCode(Texts.clean(request.postalCode()))
                .emergencyContactName(Texts.clean(request.emergencyContactName()))
                .emergencyContactPhone(Texts.phone(request.emergencyContactPhone()))
                .active(true)
                // A patient registering themselves or a dependent owns the record; reception walk-ins have no owner yet.
                .owner(currentUser.isPatient() ? userRepository.getReferenceById(currentUser.id()) : null)
                .build();

        Patient saved = patientRepository.save(patient);
        return ResponseHandler.generateResponse(
                HttpStatus.CREATED,
                PatientResponse.from(saved),
                new Status(201, messageUtils.getMessage("patient.add.success")));
    }

    @Transactional
    public ResponseEntity<Object> updatePatient(Long id, PatientRequest request) {
        Patient existing = findAccessible(id);
        String name = request.fullName().trim();
        String phone = Texts.phone(request.phone());

        if (patientRepository.existsByFullNameIgnoreCaseAndDateOfBirthAndPhoneAndIdNot(
                name, request.dateOfBirth(), phone, id)) {
            throw new AlreadyExistException(messageUtils.getMessage("patient.exists"));
        }

        existing.setFullName(name);
        existing.setDateOfBirth(request.dateOfBirth());
        existing.setGender(request.gender());
        existing.setPhone(phone);
        existing.setEmail(Texts.email(request.email()));
        existing.setBloodGroup(request.bloodGroup());
        existing.setAddressLine1(Texts.clean(request.addressLine1()));
        existing.setAddressLine2(Texts.clean(request.addressLine2()));
        existing.setCity(Texts.clean(request.city()));
        existing.setState(Texts.clean(request.state()));
        existing.setPostalCode(Texts.clean(request.postalCode()));
        existing.setEmergencyContactName(Texts.clean(request.emergencyContactName()));
        existing.setEmergencyContactPhone(Texts.phone(request.emergencyContactPhone()));
        // uhid never changes.

        Patient saved = patientRepository.save(existing);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                PatientResponse.from(saved),
                new Status(200, messageUtils.getMessage("patient.update.success")));
    }

    @Transactional
    public ResponseEntity<Object> updatePatientStatus(Long id, ActiveStatusRequest request) {
        Patient existing = find(id);
        existing.setActive(request.active());
        Patient saved = patientRepository.save(existing);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                PatientResponse.from(saved),
                new Status(200, messageUtils.getMessage("patient.status.success")));
    }

    @Transactional
    public ResponseEntity<Object> deletePatient(Long id) {
        Patient patient = find(id);
        patientRepository.delete(patient);
        // Flush so a constraint failure (future appointments/records) surfaces inside this transaction.
        patientRepository.flush();
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                null,
                new Status(200, messageUtils.getMessage("patient.delete.success")));
    }

    private Patient find(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("patient.notfound")));
    }

    /** find + record access rule. 404 rather than 403, so probing ids reveals nothing about other patients. */
    private Patient findAccessible(Long id) {
        Patient patient = find(id);
        boolean allowed;
        if (currentUser.isPatient()) {
            allowed = patient.getOwner() != null && patient.getOwner().getId().equals(currentUser.id());
        } else if (currentUser.isDoctor()) {
            allowed = appointmentRepository.existsByDoctor_IdAndPatient_Id(currentUser.doctorId(), id);
        } else {
            allowed = true;
        }
        if (!allowed) {
            throw new NotFoundException(messageUtils.getMessage("patient.notfound"));
        }
        return patient;
    }
}
