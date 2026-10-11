package com.mediconnect.service;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.dto.pharmacy.BatchFiltersRequest;
import com.mediconnect.dto.pharmacy.BatchRequest;
import com.mediconnect.dto.pharmacy.BatchResponse;
import com.mediconnect.dto.pharmacy.BatchUpdateRequest;
import com.mediconnect.dto.pharmacy.StockAdjustmentRequest;
import com.mediconnect.dto.pharmacy.StockAdjustmentResponse;
import com.mediconnect.entity.Medicine;
import com.mediconnect.entity.MedicineBatch;
import com.mediconnect.entity.StockAdjustment;
import com.mediconnect.exception.AlreadyExistException;
import com.mediconnect.exception.BadRequestException;
import com.mediconnect.exception.NotFoundException;
import com.mediconnect.helper.HospitalZone;
import com.mediconnect.repository.MedicineBatchRepository;
import com.mediconnect.repository.MedicineRepository;
import com.mediconnect.repository.StockAdjustmentRepository;
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

import java.time.Instant;
import java.time.LocalDate;

/**
 * Pharmacy stock, kept per batch (each delivery has its own batch number, expiry date and price).
 *  - Receiving stock creates a batch. Its label data (number, expiry, price) can be corrected; its quantities never
 *    are: they change only by dispensing and by stock adjustments, which are logged.
 *  - A batch can be deleted only while untouched (nothing dispensed or adjusted): a wrong entry.
 * Pharmacist and admin only.
 */
@Service
@RequiredArgsConstructor
public class BatchService {

    private final MedicineBatchRepository batchRepository;
    private final MedicineRepository medicineRepository;
    private final StockAdjustmentRepository adjustmentRepository;
    private final UserRepository userRepository;
    private final HospitalZone hospitalZone;
    private final CurrentUser currentUser;
    private final MessageUtils messageUtils;

    // -------------------------------------------------------------------------
    // GET ALL (stock and expiry report)
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getBatches(BatchFiltersRequest filters, Pageable pageable) {
        if (filters == null) {
            filters = new BatchFiltersRequest(null, null, null);
        }
        if (filters.expiringWithinDays() != null && filters.expiringWithinDays() < 0) {
            throw new BadRequestException(messageUtils.getMessage("batch.filter.days"));
        }
        LocalDate today = today();
        LocalDate expiringBy = filters.expiringWithinDays() != null ? today.plusDays(filters.expiringWithinDays()) : null;
        Page<BatchResponse> page = batchRepository.findAllWithFilters(
                        filters.medicineId(), expiringBy, Boolean.TRUE.equals(filters.inStockOnly()), pageable)
                .map(b -> BatchResponse.from(b, today));
        return ResponseHandler.generatePageResponse(
                HttpStatus.OK,
                PageResponse.from(page),
                new Status(200, messageUtils.getMessage("batch.list.success")));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Object> getBatchById(Long id) {
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                BatchResponse.from(find(id), today()),
                new Status(200, messageUtils.getMessage("batch.get.success", String.valueOf(id))));
    }

    // -------------------------------------------------------------------------
    // RECEIVE STOCK
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> addBatch(BatchRequest request) {
        Medicine medicine = medicineRepository.findById(request.medicineId())
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("medicine.notfound")));
        if (!medicine.isActive()) {
            throw new BadRequestException(messageUtils.getMessage("medicine.inactive"));
        }
        LocalDate today = today();
        if (!request.expiryDate().isAfter(today)) {
            throw new BadRequestException(messageUtils.getMessage("batch.expiry.past"));
        }
        String batchNumber = request.batchNumber().trim();
        if (batchRepository.existsByMedicine_IdAndBatchNumberIgnoreCase(medicine.getId(), batchNumber)) {
            throw new AlreadyExistException(messageUtils.getMessage("batch.exists", batchNumber));
        }
        MedicineBatch saved = batchRepository.save(MedicineBatch.builder()
                .medicine(medicine)
                .batchNumber(batchNumber)
                .expiryDate(request.expiryDate())
                .quantityReceived(request.quantity())
                .quantityOnHand(request.quantity())
                .unitPrice(request.unitPrice())
                .receivedAt(Instant.now())
                .receivedBy(userRepository.getReferenceById(currentUser.id()))
                .build());
        return ResponseHandler.generateResponse(
                HttpStatus.CREATED,
                BatchResponse.from(find(saved.getId()), today),
                new Status(201, messageUtils.getMessage("batch.add.success")));
    }

    // -------------------------------------------------------------------------
    // ADJUST STOCK (damaged, expired, returned, counting correction)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> addAdjustment(Long batchId, StockAdjustmentRequest request) {
        // Row lock: a dispense of the same batch waits, so both never start from the same quantity.
        MedicineBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("batch.notfound")));
        int change = request.quantityChange();
        String reason = request.reason();
        String note = Texts.clean(request.note());
        if (change == 0) {
            throw new BadRequestException(messageUtils.getMessage("adjustment.zero"));
        }
        if (("DAMAGED".equals(reason) || "EXPIRED".equals(reason)) && change > 0) {
            throw new BadRequestException(messageUtils.getMessage("adjustment.must.remove", reason));
        }
        if ("RETURNED".equals(reason) && change < 0) {
            throw new BadRequestException(messageUtils.getMessage("adjustment.must.add"));
        }
        if ("OTHER".equals(reason) && note == null) {
            throw new BadRequestException(messageUtils.getMessage("adjustment.note.required"));
        }
        int after = batch.getQuantityOnHand() + change;
        if (after < 0) {
            throw new BadRequestException(messageUtils.getMessage(
                    "adjustment.negative", String.valueOf(batch.getQuantityOnHand())));
        }
        batch.setQuantityOnHand(after);
        batchRepository.save(batch);
        StockAdjustment saved = adjustmentRepository.save(StockAdjustment.builder()
                .batch(batch)
                .quantityChange(change)
                .quantityAfter(after)
                .reason(reason)
                .note(note)
                .adjustedBy(userRepository.getReferenceById(currentUser.id()))
                .adjustedAt(Instant.now())
                .build());
        return ResponseHandler.generateResponse(
                HttpStatus.CREATED,
                StockAdjustmentResponse.from(saved),
                new Status(201, messageUtils.getMessage("adjustment.add.success", String.valueOf(after))));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Object> getAdjustments(Long batchId, Pageable pageable) {
        find(batchId);
        Page<StockAdjustmentResponse> page = adjustmentRepository.findByBatchId(batchId, pageable)
                .map(StockAdjustmentResponse::from);
        return ResponseHandler.generatePageResponse(
                HttpStatus.OK,
                PageResponse.from(page),
                new Status(200, messageUtils.getMessage("adjustment.list.success")));
    }

    // -------------------------------------------------------------------------
    // UPDATE (label data only)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> updateBatch(Long id, BatchUpdateRequest request) {
        MedicineBatch batch = find(id);
        String batchNumber = request.batchNumber().trim();
        if (batchRepository.existsByMedicine_IdAndBatchNumberIgnoreCaseAndIdNot(
                batch.getMedicine().getId(), batchNumber, id)) {
            throw new AlreadyExistException(messageUtils.getMessage("batch.exists", batchNumber));
        }
        batch.setBatchNumber(batchNumber);
        batch.setExpiryDate(request.expiryDate());
        // Applies to future sales only: dispensed items keep the price they were sold at.
        batch.setUnitPrice(request.unitPrice());
        MedicineBatch saved = batchRepository.save(batch);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                BatchResponse.from(saved, today()),
                new Status(200, messageUtils.getMessage("batch.update.success")));
    }

    // -------------------------------------------------------------------------
    // DELETE (a wrong entry, while nothing has been taken from it)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> deleteBatch(Long id) {
        MedicineBatch batch = find(id);
        if (batch.getQuantityOnHand() != batch.getQuantityReceived()) {
            throw new BadRequestException(messageUtils.getMessage("batch.delete.used"));
        }
        batchRepository.delete(batch);
        // Adjustments that cancel out (or dispensed + returned) still reference it: the foreign key refuses (409).
        batchRepository.flush();
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                null,
                new Status(200, messageUtils.getMessage("batch.delete.success")));
    }

    private MedicineBatch find(Long id) {
        return batchRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("batch.notfound")));
    }

    private LocalDate today() {
        return LocalDate.now(hospitalZone.get());
    }
}
