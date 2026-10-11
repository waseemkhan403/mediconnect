package com.mediconnect.service;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.dto.pharmacy.MedicineFiltersRequest;
import com.mediconnect.dto.pharmacy.MedicineRequest;
import com.mediconnect.dto.pharmacy.MedicineResponse;
import com.mediconnect.entity.Medicine;
import com.mediconnect.exception.AlreadyExistException;
import com.mediconnect.exception.BadRequestException;
import com.mediconnect.exception.NotFoundException;
import com.mediconnect.helper.HospitalZone;
import com.mediconnect.repository.MedicineBatchRepository;
import com.mediconnect.repository.MedicineRepository;
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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The pharmacy catalogue. Written by the pharmacist or an admin, read by all staff (doctors pick from it when
 * prescribing). Every response carries the usable stock (non-expired batches) and a low-stock flag.
 */
@Service
@RequiredArgsConstructor
public class MedicineService {

    private final MedicineRepository medicineRepository;
    private final MedicineBatchRepository batchRepository;
    private final HospitalZone hospitalZone;
    private final MessageUtils messageUtils;

    // -------------------------------------------------------------------------
    // GET ALL (paginated + filtered)
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getMedicines(MedicineFiltersRequest filters, Pageable pageable) {
        if (filters == null) {
            filters = new MedicineFiltersRequest(null, null, null, null);
        }
        LocalDate today = LocalDate.now(hospitalZone.get());
        Page<Medicine> medicines = medicineRepository.findAllWithFilters(
                Texts.clean(filters.name()), Texts.clean(filters.form()), filters.active(),
                Boolean.TRUE.equals(filters.lowStock()), today, pageable);
        Map<Long, Long> stock = usableStock(medicines.getContent().stream().map(Medicine::getId).toList(), today);
        Page<MedicineResponse> page = medicines.map(m -> MedicineResponse.from(m, stock.getOrDefault(m.getId(), 0L)));
        return ResponseHandler.generatePageResponse(
                HttpStatus.OK,
                PageResponse.from(page),
                new Status(200, messageUtils.getMessage("medicine.list.success")));
    }

    // -------------------------------------------------------------------------
    // GET BY ID
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getMedicineById(Long id) {
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                toResponse(find(id)),
                new Status(200, messageUtils.getMessage("medicine.get.success", String.valueOf(id))));
    }

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> addMedicine(MedicineRequest request) {
        String name = request.name().trim();
        String strength = Texts.clean(request.strength());
        if (medicineRepository.existsDuplicate(name, request.form(), strength, null)) {
            throw new AlreadyExistException(messageUtils.getMessage("medicine.exists"));
        }
        Medicine saved = medicineRepository.save(Medicine.builder()
                .name(name)
                .genericName(Texts.clean(request.genericName()))
                .form(request.form())
                .strength(strength)
                .manufacturer(Texts.clean(request.manufacturer()))
                .reorderLevel(request.reorderLevel() != null ? request.reorderLevel() : 0)
                .active(request.active() == null || request.active())
                .build());
        return ResponseHandler.generateResponse(
                HttpStatus.CREATED,
                toResponse(saved),
                new Status(201, messageUtils.getMessage("medicine.add.success")));
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> updateMedicine(Long id, MedicineRequest request) {
        Medicine existing = find(id);
        String name = request.name().trim();
        String strength = Texts.clean(request.strength());
        if (medicineRepository.existsDuplicate(name, request.form(), strength, id)) {
            throw new AlreadyExistException(messageUtils.getMessage("medicine.exists"));
        }
        existing.setName(name);
        existing.setGenericName(Texts.clean(request.genericName()));
        existing.setForm(request.form());
        existing.setStrength(strength);
        existing.setManufacturer(Texts.clean(request.manufacturer()));
        existing.setReorderLevel(request.reorderLevel() != null ? request.reorderLevel() : 0);
        if (request.active() != null) {
            existing.setActive(request.active());
        }
        Medicine saved = medicineRepository.save(existing);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                toResponse(saved),
                new Status(200, messageUtils.getMessage("medicine.update.success")));
    }

    // -------------------------------------------------------------------------
    // DELETE (only a medicine never stocked or prescribed; otherwise deactivate it)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> deleteMedicine(Long id) {
        Medicine medicine = find(id);
        if (batchRepository.existsByMedicine_Id(id)) {
            throw new BadRequestException(messageUtils.getMessage("medicine.delete.inuse"));
        }
        medicineRepository.delete(medicine);
        // A prescription line that links to it makes the foreign key refuse this (409).
        medicineRepository.flush();
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                null,
                new Status(200, messageUtils.getMessage("medicine.delete.success")));
    }

    private Medicine find(Long id) {
        return medicineRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("medicine.notfound")));
    }

    private MedicineResponse toResponse(Medicine m) {
        LocalDate today = LocalDate.now(hospitalZone.get());
        return MedicineResponse.from(m, usableStock(List.of(m.getId()), today).getOrDefault(m.getId(), 0L));
    }

    private Map<Long, Long> usableStock(List<Long> medicineIds, LocalDate today) {
        Map<Long, Long> stock = new HashMap<>();
        if (medicineIds.isEmpty()) {
            return stock;
        }
        for (Object[] row : medicineRepository.sumUsableStock(medicineIds, today)) {
            stock.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return stock;
    }
}
