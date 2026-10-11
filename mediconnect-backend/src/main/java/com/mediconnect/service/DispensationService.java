package com.mediconnect.service;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.dto.pharmacy.DispensationFiltersRequest;
import com.mediconnect.dto.pharmacy.DispensationListResponse;
import com.mediconnect.dto.pharmacy.DispensationResponse;
import com.mediconnect.dto.pharmacy.DispenseItemRequest;
import com.mediconnect.dto.pharmacy.DispenseRequest;
import com.mediconnect.dto.pharmacy.PrescriptionQueueFiltersRequest;
import com.mediconnect.dto.pharmacy.PrescriptionQueueResponse;
import com.mediconnect.entity.Appointment;
import com.mediconnect.entity.Dispensation;
import com.mediconnect.entity.DispensationItem;
import com.mediconnect.entity.Encounter;
import com.mediconnect.entity.Medicine;
import com.mediconnect.entity.MedicineBatch;
import com.mediconnect.entity.PrescriptionItem;
import com.mediconnect.entity.User;
import com.mediconnect.exception.BadRequestException;
import com.mediconnect.exception.NotFoundException;
import com.mediconnect.helper.HospitalZone;
import com.mediconnect.repository.DispensationRepository;
import com.mediconnect.repository.EncounterRepository;
import com.mediconnect.repository.MedicineBatchRepository;
import com.mediconnect.repository.MedicineRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Handing over prescribed medicines at the pharmacy counter.
 *  - The pharmacist works from a queue of FINAL encounters with a prescription (medicines only, no clinical notes).
 *  - Dispensing takes stock from the earliest-expiring usable batches first (FEFO) and may split a line over
 *    several batches. Not enough stock = 400 and nothing is taken (one transaction).
 *  - Batches are locked (SELECT ... FOR UPDATE) for the whole transaction, medicines in ascending id order, so two
 *    pharmacists cannot sell the same units and cannot deadlock each other.
 *  - Partial fills are allowed: an encounter may get several dispensations. Dispensations are never edited or
 *    deleted; returns go back into stock as a RETURNED stock adjustment.
 * Who sees dispensations: pharmacist and admin all; a doctor those of their own encounters; a patient those of the
 * patients they own; anyone else 404.
 */
@Service
@RequiredArgsConstructor
public class DispensationService {

    private static final Instant MIN_INSTANT = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant MAX_INSTANT = Instant.parse("9999-12-31T00:00:00Z");

    private final DispensationRepository dispensationRepository;
    private final EncounterRepository encounterRepository;
    private final MedicineRepository medicineRepository;
    private final MedicineBatchRepository batchRepository;
    private final UserRepository userRepository;
    private final HospitalZone hospitalZone;
    private final CurrentUser currentUser;
    private final MessageUtils messageUtils;

    // -------------------------------------------------------------------------
    // PRESCRIPTION QUEUE (pharmacist / admin)
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getQueue(PrescriptionQueueFiltersRequest filters, Pageable pageable) {
        if (filters == null) {
            filters = new PrescriptionQueueFiltersRequest(null, null);
        }
        boolean pendingOnly = filters.pendingOnly() == null || filters.pendingOnly();
        Page<Encounter> encounters = encounterRepository.findPrescriptionQueue(filters.patientId(), pendingOnly, pageable);
        List<Long> ids = encounters.getContent().stream().map(Encounter::getId).toList();
        Set<Long> dispensed = ids.isEmpty() ? Set.of() : new HashSet<>(dispensationRepository.findDispensedEncounterIds(ids));
        ZoneId zone = hospitalZone.get();
        Page<PrescriptionQueueResponse> page = encounters.map(
                e -> PrescriptionQueueResponse.from(e, dispensed.contains(e.getId()), zone));
        return ResponseHandler.generatePageResponse(
                HttpStatus.OK,
                PageResponse.from(page),
                new Status(200, messageUtils.getMessage("dispense.queue.success")));
    }

    // -------------------------------------------------------------------------
    // GET ALL (paginated + filtered, scoped to the caller)
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getDispensations(DispensationFiltersRequest filters, Pageable pageable) {
        if (filters == null) {
            filters = new DispensationFiltersRequest(null, null, null, null, null);
        }
        if (filters.dateFrom() != null && filters.dateTo() != null && filters.dateTo().isBefore(filters.dateFrom())) {
            throw new BadRequestException(messageUtils.getMessage("dispense.filter.dates"));
        }
        ZoneId zone = hospitalZone.get();
        Long doctorId = currentUser.isDoctor() ? currentUser.doctorId() : filters.doctorId();
        Long ownerId = currentUser.isPatient() ? currentUser.id() : null;
        Page<DispensationListResponse> page = dispensationRepository.findAllWithFilters(
                        filters.encounterId(), filters.patientId(), doctorId, ownerId,
                        filters.dateFrom() != null ? filters.dateFrom().atStartOfDay(zone).toInstant() : MIN_INSTANT,
                        filters.dateTo() != null ? filters.dateTo().plusDays(1).atStartOfDay(zone).toInstant() : MAX_INSTANT,
                        pageable)
                .map(DispensationListResponse::from);
        return ResponseHandler.generatePageResponse(
                HttpStatus.OK,
                PageResponse.from(page),
                new Status(200, messageUtils.getMessage("dispense.list.success")));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Object> getDispensationById(Long id) {
        Dispensation dispensation = dispensationRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("dispense.notfound")));
        Appointment appointment = dispensation.getEncounter().getAppointment();
        boolean allowed;
        if (currentUser.isPatient()) {
            User owner = appointment.getPatient().getOwner();
            allowed = owner != null && owner.getId().equals(currentUser.id());
        } else if (currentUser.isDoctor()) {
            allowed = appointment.getDoctor().getId().equals(currentUser.doctorId());
        } else {
            allowed = true;
        }
        if (!allowed) {
            throw new NotFoundException(messageUtils.getMessage("dispense.notfound"));
        }
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                DispensationResponse.from(dispensation),
                new Status(200, messageUtils.getMessage("dispense.get.success", String.valueOf(id))));
    }

    // -------------------------------------------------------------------------
    // DISPENSE (pharmacist)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> dispense(DispenseRequest request) {
        Encounter encounter = encounterRepository.findDetailById(request.encounterId())
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("encounter.notfound")));
        if (!"FINAL".equals(encounter.getStatus())) {
            throw new BadRequestException(messageUtils.getMessage("dispense.encounter.notfinal"));
        }
        Map<Long, PrescriptionItem> lines = encounter.getPrescriptionItems().stream()
                .collect(Collectors.toMap(PrescriptionItem::getId, Function.identity()));

        // 1. Validate every requested line and decide which medicine it gets.
        Set<Long> seenLines = new HashSet<>();
        Map<DispenseItemRequest, Medicine> medicineFor = new HashMap<>();
        for (DispenseItemRequest item : request.items()) {
            PrescriptionItem line = lines.get(item.prescriptionItemId());
            if (line == null) {
                throw new BadRequestException(messageUtils.getMessage(
                        "dispense.line.invalid", String.valueOf(item.prescriptionItemId())));
            }
            if (!seenLines.add(line.getId())) {
                throw new BadRequestException(messageUtils.getMessage(
                        "dispense.line.duplicate", String.valueOf(line.getId())));
            }
            Long medicineId = item.medicineId() != null ? item.medicineId()
                    : line.getMedicine() != null ? line.getMedicine().getId() : null;
            if (medicineId == null) {
                throw new BadRequestException(messageUtils.getMessage("dispense.medicine.required", line.getMedicineName()));
            }
            Medicine medicine = medicineRepository.findById(medicineId)
                    .filter(Medicine::isActive)
                    .orElseThrow(() -> new BadRequestException(messageUtils.getMessage(
                            "dispense.medicine.invalid", String.valueOf(medicineId))));
            medicineFor.put(item, medicine);
        }

        // 2. Lock the usable batches of every medicine involved, always in ascending medicine id order: two
        //    transactions then take locks in the same order and cannot deadlock.
        LocalDate today = LocalDate.now(hospitalZone.get());
        Map<Long, List<MedicineBatch>> batchesByMedicine = new HashMap<>();
        for (Long medicineId : new TreeSet<>(medicineFor.values().stream().map(Medicine::getId).toList())) {
            batchesByMedicine.put(medicineId, batchRepository.findDispensableForUpdate(medicineId, today));
        }

        // 3. Allocate earliest expiry first, splitting a line over batches when needed.
        Dispensation dispensation = Dispensation.builder()
                .encounter(encounter)
                .dispensedBy(userRepository.getReferenceById(currentUser.id()))
                .dispensedAt(Instant.now())
                .notes(Texts.clean(request.notes()))
                .totalAmount(BigDecimal.ZERO)
                .build();
        BigDecimal total = BigDecimal.ZERO;
        for (DispenseItemRequest item : request.items()) {
            Medicine medicine = medicineFor.get(item);
            List<MedicineBatch> batches = batchesByMedicine.get(medicine.getId());
            int available = batches.stream().mapToInt(MedicineBatch::getQuantityOnHand).sum();
            if (available < item.quantity()) {
                throw new BadRequestException(messageUtils.getMessage("dispense.stock.insufficient",
                        medicine.getName(), String.valueOf(available), String.valueOf(item.quantity())));
            }
            int needed = item.quantity();
            for (MedicineBatch batch : batches) {
                if (needed == 0) {
                    break;
                }
                int take = Math.min(needed, batch.getQuantityOnHand());
                if (take == 0) {
                    continue;
                }
                batch.setQuantityOnHand(batch.getQuantityOnHand() - take);
                needed -= take;
                BigDecimal lineTotal = batch.getUnitPrice().multiply(BigDecimal.valueOf(take));
                total = total.add(lineTotal);
                dispensation.getItems().add(DispensationItem.builder()
                        .dispensation(dispensation)
                        .prescriptionItem(lines.get(item.prescriptionItemId()))
                        .medicine(medicine)
                        .batch(batch)
                        .quantity(take)
                        .unitPrice(batch.getUnitPrice())
                        .lineTotal(lineTotal)
                        .build());
            }
        }
        dispensation.setTotalAmount(total);
        Dispensation saved = dispensationRepository.saveAndFlush(dispensation);
        return ResponseHandler.generateResponse(
                HttpStatus.CREATED,
                DispensationResponse.from(saved),
                new Status(201, messageUtils.getMessage("dispense.add.success")));
    }
}
