package com.mediconnect.service;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.dto.department.DepartmentFiltersRequest;
import com.mediconnect.dto.department.DepartmentRequest;
import com.mediconnect.dto.department.DepartmentResponse;
import com.mediconnect.entity.Department;
import com.mediconnect.exception.AlreadyExistException;
import com.mediconnect.exception.BadRequestException;
import com.mediconnect.exception.NotFoundException;
import com.mediconnect.repository.DepartmentRepository;
import com.mediconnect.repository.StaffRepository;
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
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final StaffRepository staffRepository;
    private final MessageUtils messageUtils;

    // -------------------------------------------------------------------------
    // GET ALL (paginated + filtered)
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getDepartments(DepartmentFiltersRequest filters, Pageable pageable) {
        if (filters == null) {
            filters = new DepartmentFiltersRequest(null, null);
        }
        Page<DepartmentResponse> page = departmentRepository
                .findAllWithFilters(Texts.clean(filters.name()), filters.active(), pageable)
                .map(DepartmentResponse::from);
        return ResponseHandler.generatePageResponse(
                HttpStatus.OK,
                PageResponse.from(page),
                new Status(200, messageUtils.getMessage("department.list.success")));
    }

    // -------------------------------------------------------------------------
    // GET ACTIVE (dropdowns, public pages: not paged)
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getActiveDepartments() {
        List<DepartmentResponse> departments = departmentRepository.findByActiveTrueOrderByNameAsc()
                .stream().map(DepartmentResponse::from).toList();
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                departments,
                new Status(200, messageUtils.getMessage("department.list.success")));
    }

    // -------------------------------------------------------------------------
    // GET BY ID
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getDepartmentById(Long id) {
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                DepartmentResponse.from(find(id)),
                new Status(200, messageUtils.getMessage("department.get.success", String.valueOf(id))));
    }

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> addDepartment(DepartmentRequest request) {
        String name = request.name().trim();
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw new AlreadyExistException(messageUtils.getMessage("department.exists", name));
        }
        Department saved = departmentRepository.save(Department.builder()
                .name(name)
                .description(Texts.clean(request.description()))
                .active(request.active() == null || request.active())
                .build());
        return ResponseHandler.generateResponse(
                HttpStatus.CREATED,
                DepartmentResponse.from(saved),
                new Status(201, messageUtils.getMessage("department.add.success")));
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> updateDepartment(Long id, DepartmentRequest request) {
        Department existing = find(id);
        String name = request.name().trim();
        if (departmentRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new AlreadyExistException(messageUtils.getMessage("department.exists", name));
        }
        existing.setName(name);
        existing.setDescription(Texts.clean(request.description()));
        if (request.active() != null) {
            existing.setActive(request.active());
        }
        Department saved = departmentRepository.save(existing);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                DepartmentResponse.from(saved),
                new Status(200, messageUtils.getMessage("department.update.success")));
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> deleteDepartment(Long id) {
        Department department = find(id);
        // Friendly message first; the foreign key still protects us if something slips past.
        if (staffRepository.existsByDepartmentId(id)) {
            throw new BadRequestException(messageUtils.getMessage("department.delete.inuse"));
        }
        departmentRepository.delete(department);
        // DELETE is otherwise only sent at commit; flush now so a constraint error surfaces
        // inside this transaction and reaches GlobalExceptionHandler.
        departmentRepository.flush();
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                null,
                new Status(200, messageUtils.getMessage("department.delete.success")));
    }

    private Department find(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("department.notfound")));
    }
}
