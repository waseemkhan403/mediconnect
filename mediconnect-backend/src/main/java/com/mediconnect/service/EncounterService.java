package com.mediconnect.service;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.dto.encounter.AddendumRequest;
import com.mediconnect.dto.encounter.EncounterCreateRequest;
import com.mediconnect.dto.encounter.EncounterFiltersRequest;
import com.mediconnect.dto.encounter.EncounterListResponse;
import com.mediconnect.dto.encounter.EncounterRequest;
import com.mediconnect.dto.encounter.EncounterResponse;
import com.mediconnect.dto.encounter.PrescriptionItemRequest;
import com.mediconnect.dto.encounter.VitalsRequest;
import com.mediconnect.entity.Appointment;
import com.mediconnect.entity.Encounter;
import com.mediconnect.entity.EncounterAddendum;
import com.mediconnect.entity.Medicine;
import com.mediconnect.entity.PrescriptionItem;
import com.mediconnect.exception.AlreadyExistException;
import com.mediconnect.exception.BadRequestException;
import com.mediconnect.exception.NotFoundException;
import com.mediconnect.helper.HospitalZone;
import com.mediconnect.repository.AppointmentRepository;
import com.mediconnect.repository.EncounterRepository;
import com.mediconnect.repository.MedicineRepository;
import com.mediconnect.response.PageResponse;
import com.mediconnect.response.ResponseHandler;
import com.mediconnect.response.Status;
import com.mediconnect.security.CurrentUser;
import com.mediconnect.util.Texts;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * The clinical record of a consultation.
 *  - Opened (DRAFT) by a nurse or the appointment's doctor once the patient is CHECKED_IN or IN_CONSULTATION.
 *  - Nurse or doctor record vitals; only the appointment's doctor writes notes, diagnosis, advice and prescription.
 *  - The doctor finalizes it (diagnosis required): it becomes FINAL and locked, and the appointment COMPLETED.
 *    After that only addenda can be appended; a FINAL encounter is never edited or deleted.
 *    Finalizing also opens the patient-doctor chat for this encounter (ChatService).
 * Who sees what: admins and nurses all encounters; a doctor their own, plus other doctors' FINAL encounters of a
 * patient whose consent they hold; a patient only FINAL encounters of the patient records they own.
 * Anything else is "not found".
 */
@Service
@RequiredArgsConstructor
public class EncounterService {

    /** The patient must have arrived before a clinical record is opened. */
    private static final Set<String> OPENABLE = Set.of("CHECKED_IN", "IN_CONSULTATION");

    private static final Instant MIN_INSTANT = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant MAX_INSTANT = Instant.parse("9999-12-31T00:00:00Z");

    private final EncounterRepository encounterRepository;
    private final AppointmentRepository appointmentRepository;
    private final MedicineRepository medicineRepository;
    private final ConsentService consentService;
    private final ChatService chatService;
    private final HospitalZone hospitalZone;
    private final CurrentUser currentUser;
    private final MessageUtils messageUtils;

    // -------------------------------------------------------------------------
    // GET ALL (paginated + filtered). A patient's history = this list for a patientId.
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getEncounters(EncounterFiltersRequest filters, Pageable pageable) {
        if (filters == null) {
            filters = new EncounterFiltersRequest(null, null, null, null, null, null);
        }
        if (filters.dateFrom() != null && filters.dateTo() != null && filters.dateTo().isBefore(filters.dateFrom())) {
            throw new BadRequestException(messageUtils.getMessage("encounter.filter.dates"));
        }
        ZoneId zone = hospitalZone.get();
        // Forced server-side: patients see their own records, and only FINAL ones. Doctors see their own encounters,
        // or, for a patient whose consent they hold, that patient's whole FINAL history plus their own drafts.
        Long doctorId = filters.doctorId();
        Long viewerDoctorId = null;
        if (currentUser.isDoctor()) {
            if (consentService.hasActiveConsent(filters.patientId(), currentUser.doctorId())) {
                viewerDoctorId = currentUser.doctorId();
            } else {
                doctorId = currentUser.doctorId();
            }
        }
        Long ownerId = currentUser.isPatient() ? currentUser.id() : null;
        String status = currentUser.isPatient() ? "FINAL" : Texts.clean(filters.status());

        Page<EncounterListResponse> page = encounterRepository.findAllWithFilters(
                        filters.appointmentId(),
                        filters.patientId(),
                        doctorId,
                        ownerId,
                        viewerDoctorId,
                        status,
                        filters.dateFrom() != null ? filters.dateFrom().atStartOfDay(zone).toInstant() : MIN_INSTANT,
                        filters.dateTo() != null ? filters.dateTo().plusDays(1).atStartOfDay(zone).toInstant() : MAX_INSTANT,
                        pageable)
                .map(e -> EncounterListResponse.from(e, zone));
        return ResponseHandler.generatePageResponse(
                HttpStatus.OK,
                PageResponse.from(page),
                new Status(200, messageUtils.getMessage("encounter.list.success")));
    }

    // -------------------------------------------------------------------------
    // GET BY ID (full record: vitals, notes, prescription, addenda)
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getEncounterById(Long id) {
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                EncounterResponse.from(findReadable(id), hospitalZone.get()),
                new Status(200, messageUtils.getMessage("encounter.get.success", String.valueOf(id))));
    }

    // -------------------------------------------------------------------------
    // OPEN (nurse or the appointment's doctor)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> addEncounter(EncounterCreateRequest request) {
        Appointment appointment = appointmentRepository.findDetailById(request.appointmentId())
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("appointment.notfound")));
        if (currentUser.isDoctor() && !appointment.getDoctor().getId().equals(currentUser.doctorId())) {
            throw new NotFoundException(messageUtils.getMessage("appointment.notfound"));
        }
        if (!OPENABLE.contains(appointment.getStatus())) {
            throw new BadRequestException(messageUtils.getMessage("encounter.appointment.notready", appointment.getStatus()));
        }
        if (encounterRepository.existsByAppointment_Id(appointment.getId())) {
            throw new AlreadyExistException(messageUtils.getMessage("encounter.exists"));
        }
        Encounter saved;
        try {
            // Flush now so a race on the unique appointment_id gives the same clear 409.
            saved = encounterRepository.saveAndFlush(Encounter.builder()
                    .appointment(appointment)
                    .status("DRAFT")
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new AlreadyExistException(messageUtils.getMessage("encounter.exists"));
        }
        return ResponseHandler.generateResponse(
                HttpStatus.CREATED,
                EncounterResponse.from(saved, hospitalZone.get()),
                new Status(201, messageUtils.getMessage("encounter.add.success")));
    }

    // -------------------------------------------------------------------------
    // UPDATE the clinical record (doctor, DRAFT only). Replaces notes and the whole prescription.
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> updateEncounter(Long id, EncounterRequest request) {
        Encounter encounter = findDraft(id);
        ZoneId zone = hospitalZone.get();
        LocalDate visitDate = encounter.getAppointment().getSlotStart().atZone(zone).toLocalDate();
        if (request.followUpDate() != null && !request.followUpDate().isAfter(visitDate)) {
            throw new BadRequestException(messageUtils.getMessage("encounter.followup.invalid"));
        }

        encounter.setChiefComplaint(Texts.clean(request.chiefComplaint()));
        encounter.setClinicalNotes(Texts.clean(request.clinicalNotes()));
        encounter.setDiagnosis(Texts.clean(request.diagnosis()));
        encounter.setAdvice(Texts.clean(request.advice()));
        encounter.setFollowUpDate(request.followUpDate());

        // Clear and refill the same list: orphanRemoval deletes the dropped rows (replacing the list object would not).
        encounter.getPrescriptionItems().clear();
        List<PrescriptionItemRequest> items = request.prescriptions() != null ? request.prescriptions() : List.of();
        for (PrescriptionItemRequest item : items) {
            encounter.getPrescriptionItems().add(PrescriptionItem.builder()
                    .encounter(encounter)
                    .medicine(catalogueMedicine(item.medicineId()))
                    .medicineName(item.medicineName().trim())
                    .dosage(Texts.clean(item.dosage()))
                    .frequency(Texts.clean(item.frequency()))
                    .durationDays(item.durationDays())
                    .instructions(Texts.clean(item.instructions()))
                    .build());
        }

        Encounter saved = encounterRepository.saveAndFlush(encounter);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                EncounterResponse.from(saved, zone),
                new Status(200, messageUtils.getMessage("encounter.update.success")));
    }

    // -------------------------------------------------------------------------
    // VITALS (nurse or the appointment's doctor, DRAFT only). Replaces all readings.
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> updateVitals(Long id, VitalsRequest request) {
        Encounter encounter = findDraft(id);
        if (request.bpSystolic() != null && request.bpDiastolic() != null
                && request.bpDiastolic() >= request.bpSystolic()) {
            throw new BadRequestException(messageUtils.getMessage("encounter.vitals.bp"));
        }
        encounter.setBpSystolic(request.bpSystolic());
        encounter.setBpDiastolic(request.bpDiastolic());
        encounter.setPulseRate(request.pulseRate());
        encounter.setTemperatureC(request.temperatureC());
        encounter.setSpo2(request.spo2());
        encounter.setWeightKg(request.weightKg());
        encounter.setHeightCm(request.heightCm());
        Encounter saved = encounterRepository.save(encounter);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                EncounterResponse.from(saved, hospitalZone.get()),
                new Status(200, messageUtils.getMessage("encounter.vitals.success")));
    }

    // -------------------------------------------------------------------------
    // FINALIZE (doctor): locks the record and completes the appointment
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> finalizeEncounter(Long id) {
        Encounter encounter = findDraft(id);
        if (encounter.getDiagnosis() == null) {
            throw new BadRequestException(messageUtils.getMessage("encounter.diagnosis.required"));
        }
        Appointment appointment = encounter.getAppointment();
        // Same rule as the appointment state machine: only a started consultation can be completed.
        if ("IN_CONSULTATION".equals(appointment.getStatus())) {
            appointment.setStatus("COMPLETED");
            appointmentRepository.save(appointment);
        } else if (!"COMPLETED".equals(appointment.getStatus())) {
            throw new BadRequestException(messageUtils.getMessage("encounter.finalize.notstarted", appointment.getStatus()));
        }
        encounter.setStatus("FINAL");
        encounter.setFinalizedAt(Instant.now());
        Encounter saved = encounterRepository.save(encounter);
        // Opens the follow-up chat between the patient and this doctor (same transaction).
        chatService.openThread(saved);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                EncounterResponse.from(saved, hospitalZone.get()),
                new Status(200, messageUtils.getMessage("encounter.finalize.success")));
    }

    // -------------------------------------------------------------------------
    // ADDENDUM (doctor, FINAL only): the only way to correct a signed record
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> addAddendum(Long id, AddendumRequest request) {
        Encounter encounter = findReadable(id);
        if (!"FINAL".equals(encounter.getStatus())) {
            throw new BadRequestException(messageUtils.getMessage("encounter.addendum.draft"));
        }
        encounter.getAddenda().add(EncounterAddendum.builder()
                .encounter(encounter)
                .text(request.text().trim())
                .addedAt(Instant.now())
                .build());
        Encounter saved = encounterRepository.saveAndFlush(encounter);
        return ResponseHandler.generateResponse(
                HttpStatus.CREATED,
                EncounterResponse.from(saved, hospitalZone.get()),
                new Status(201, messageUtils.getMessage("encounter.addendum.success")));
    }

    // -------------------------------------------------------------------------
    // DELETE (DRAFT only: a record opened by mistake). FINAL records are permanent.
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> deleteEncounter(Long id) {
        Encounter encounter = findDraft(id);
        encounterRepository.delete(encounter);
        encounterRepository.flush();
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                null,
                new Status(200, messageUtils.getMessage("encounter.delete.success")));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    /** Read access: patients their own FINAL records, doctors their own encounters, admins and nurses all. */
    private Encounter findReadable(Long id) {
        Encounter encounter = encounterRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("encounter.notfound")));
        Appointment appointment = encounter.getAppointment();
        boolean allowed;
        if (currentUser.isPatient()) {
            var owner = appointment.getPatient().getOwner();
            allowed = "FINAL".equals(encounter.getStatus())
                    && owner != null && owner.getId().equals(currentUser.id());
        } else if (currentUser.isDoctor()) {
            // Own encounters; or another doctor's FINAL encounter when the patient has shared their history.
            allowed = appointment.getDoctor().getId().equals(currentUser.doctorId())
                    || ("FINAL".equals(encounter.getStatus())
                        && consentService.hasActiveConsent(appointment.getPatient().getId(), currentUser.doctorId()));
        } else {
            allowed = true;
        }
        if (!allowed) {
            throw new NotFoundException(messageUtils.getMessage("encounter.notfound"));
        }
        return encounter;
    }

    /** The optional catalogue link of a prescription line: must exist and be active. */
    private Medicine catalogueMedicine(Long medicineId) {
        if (medicineId == null) {
            return null;
        }
        Medicine medicine = medicineRepository.findById(medicineId)
                .orElseThrow(() -> new BadRequestException(
                        messageUtils.getMessage("encounter.medicine.invalid", String.valueOf(medicineId))));
        if (!medicine.isActive()) {
            throw new BadRequestException(
                    messageUtils.getMessage("encounter.medicine.invalid", String.valueOf(medicineId)));
        }
        return medicine;
    }

    /** findReadable + must still be a DRAFT (FINAL records are locked). */
    private Encounter findDraft(Long id) {
        Encounter encounter = findReadable(id);
        if (!"DRAFT".equals(encounter.getStatus())) {
            throw new BadRequestException(messageUtils.getMessage("encounter.locked"));
        }
        return encounter;
    }
}
