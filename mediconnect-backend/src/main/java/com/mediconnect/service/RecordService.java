package com.mediconnect.service;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.dto.record.AccessLogFiltersRequest;
import com.mediconnect.dto.record.AccessLogResponse;
import com.mediconnect.dto.record.DownloadLinkResponse;
import com.mediconnect.dto.record.RecordFiltersRequest;
import com.mediconnect.dto.record.RecordResponse;
import com.mediconnect.dto.record.RecordUploadRequest;
import com.mediconnect.dto.record.RetractRequest;
import com.mediconnect.entity.MedicalRecord;
import com.mediconnect.entity.Patient;
import com.mediconnect.entity.RecordAccessLog;
import com.mediconnect.exception.BadRequestException;
import com.mediconnect.exception.ForbiddenException;
import com.mediconnect.exception.NotFoundException;
import com.mediconnect.exception.UnauthorizedException;
import com.mediconnect.repository.AppointmentRepository;
import com.mediconnect.repository.MedicalRecordRepository;
import com.mediconnect.repository.PatientRepository;
import com.mediconnect.repository.RecordAccessLogRepository;
import com.mediconnect.repository.UserRepository;
import com.mediconnect.response.PageResponse;
import com.mediconnect.response.ResponseHandler;
import com.mediconnect.response.Status;
import com.mediconnect.security.CurrentUser;
import com.mediconnect.security.DownloadLinks;
import com.mediconnect.storage.FileStorage;
import com.mediconnect.util.Texts;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Patients' medical documents. Append-only: no edit, no delete; a wrong upload is retracted (kept, hidden).
 * Who may read a record (open its details or download the file):
 *  - the patient login that owns the patient record,
 *  - whoever uploaded it,
 *  - a doctor holding the patient's active consent.
 * Admins may see record details (title, type, dates) for administration, but never get the file.
 * Every detail view and every download is written to the access log, which the patient can read.
 */
@Service
@RequiredArgsConstructor
public class RecordService {

    private final MedicalRecordRepository recordRepository;
    private final RecordAccessLogRepository accessLogRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final ConsentService consentService;
    private final FileStorage fileStorage;
    private final DownloadLinks downloadLinks;
    private final CurrentUser currentUser;
    private final MessageUtils messageUtils;

    // -------------------------------------------------------------------------
    // GET ALL (metadata only)
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getRecords(RecordFiltersRequest filters, Pageable pageable) {
        if (filters == null) {
            filters = new RecordFiltersRequest(null, null, null);
        }
        Long ownerId = null;
        Long uploaderId = null;
        if (currentUser.isPatient()) {
            ownerId = currentUser.id();
        } else if (currentUser.isDoctor()) {
            // With the patient's consent a doctor sees all their records; otherwise only what the doctor uploaded.
            if (!consentService.hasActiveConsent(filters.patientId(), currentUser.doctorId())) {
                uploaderId = currentUser.id();
            }
        } else if (!currentUser.isAdmin()) {
            uploaderId = currentUser.id();
        }
        Page<RecordResponse> page = recordRepository.findAllWithFilters(
                        filters.patientId(),
                        Texts.clean(filters.recordType()),
                        ownerId,
                        uploaderId,
                        Boolean.TRUE.equals(filters.includeRetracted()),
                        pageable)
                .map(RecordResponse::from);
        return ResponseHandler.generatePageResponse(
                HttpStatus.OK,
                PageResponse.from(page),
                new Status(200, messageUtils.getMessage("record.list.success")));
    }

    // -------------------------------------------------------------------------
    // GET BY ID (metadata; logged)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> getRecordById(Long id) {
        MedicalRecord record = findDetail(id);
        if (!canRead(record) && !currentUser.isAdmin()) {
            throw new NotFoundException(messageUtils.getMessage("record.notfound"));
        }
        log(record, currentUser.id(), "VIEW");
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                RecordResponse.from(record),
                new Status(200, messageUtils.getMessage("record.get.success", String.valueOf(id))));
    }

    // -------------------------------------------------------------------------
    // DOWNLOAD LINK (access checked here; the link itself carries the proof)
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getDownloadLink(Long id) {
        MedicalRecord record = findDetail(id);
        if (!canRead(record)) {
            // Admins included: they manage records but do not read patients' files.
            throw new NotFoundException(messageUtils.getMessage("record.notfound"));
        }
        Instant expiresAt = Instant.now().plus(DownloadLinks.TTL);
        String token = downloadLinks.create(record.getId(), currentUser.id(), expiresAt);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                new DownloadLinkResponse("/files/" + token, expiresAt),
                new Status(200, messageUtils.getMessage("record.link.success")));
    }

    /** GET /files/{token}: open endpoint; the signed token is the authorization. Logged as a DOWNLOAD. */
    @Transactional
    public ResponseEntity<Resource> download(String token) {
        DownloadLinks.Grant grant = downloadLinks.verify(token)
                .orElseThrow(() -> new UnauthorizedException(messageUtils.getMessage("record.link.invalid")));
        MedicalRecord record = recordRepository.findById(grant.recordId())
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("record.notfound")));
        log(record, grant.userId(), "DOWNLOAD");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(record.getContentType()))
                .contentLength(record.getSizeBytes())
                // inline: PDFs and images open in the browser tab; the name is encoded safely by ContentDisposition.
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(record.getOriginalFilename(), StandardCharsets.UTF_8).build().toString())
                // The file was validated when uploaded; never cache patient documents in shared caches.
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(fileStorage.load(record.getStorageKey()));
    }

    // -------------------------------------------------------------------------
    // UPLOAD
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> addRecord(RecordUploadRequest request, MultipartFile file) {
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("patient.notfound")));
        checkCanUpload(patient);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(messageUtils.getMessage("record.file.empty"));
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // Trust the bytes, not the client's file name or Content-Type header.
        String contentType = detectType(content);
        if (contentType == null) {
            throw new BadRequestException(messageUtils.getMessage("record.file.type"));
        }

        String key = fileStorage.save(content);
        MedicalRecord saved;
        try {
            saved = recordRepository.saveAndFlush(MedicalRecord.builder()
                    .patient(patient)
                    .title(request.title().trim())
                    .recordType(request.recordType())
                    .recordDate(request.recordDate())
                    .notes(Texts.clean(request.notes()))
                    .originalFilename(cleanFilename(file.getOriginalFilename()))
                    .contentType(contentType)
                    .sizeBytes((long) content.length)
                    .storageKey(key)
                    .uploadedBy(userRepository.findDetailById(currentUser.id()).orElse(null))
                    .uploadedAt(Instant.now())
                    .status("ACTIVE")
                    .build());
        } catch (RuntimeException e) {
            fileStorage.delete(key);
            throw e;
        }
        return ResponseHandler.generateResponse(
                HttpStatus.CREATED,
                RecordResponse.from(saved),
                new Status(201, messageUtils.getMessage("record.add.success")));
    }

    // -------------------------------------------------------------------------
    // RETRACT (the uploader or an admin): hidden from lists, never deleted
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> retractRecord(Long id, RetractRequest request) {
        MedicalRecord record = findDetail(id);
        if (!canRead(record) && !currentUser.isAdmin()) {
            throw new NotFoundException(messageUtils.getMessage("record.notfound"));
        }
        if (!isUploader(record) && !currentUser.isAdmin()) {
            throw new ForbiddenException(messageUtils.getMessage("record.retract.notown"));
        }
        if ("RETRACTED".equals(record.getStatus())) {
            throw new BadRequestException(messageUtils.getMessage("record.retracted"));
        }
        record.setStatus("RETRACTED");
        record.setRetractionReason(request.reason().trim());
        MedicalRecord saved = recordRepository.save(record);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                RecordResponse.from(saved),
                new Status(200, messageUtils.getMessage("record.retract.success")));
    }

    // -------------------------------------------------------------------------
    // ACCESS LOG (patient: their own records; admin: everything)
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getAccessLog(AccessLogFiltersRequest filters, Pageable pageable) {
        if (filters == null) {
            filters = new AccessLogFiltersRequest(null, null);
        }
        Long ownerId = currentUser.isPatient() ? currentUser.id() : null;
        Page<AccessLogResponse> page = accessLogRepository.findAllWithFilters(
                        filters.recordId(), filters.patientId(), ownerId, pageable)
                .map(AccessLogResponse::from);
        return ResponseHandler.generatePageResponse(
                HttpStatus.OK,
                PageResponse.from(page),
                new Status(200, messageUtils.getMessage("record.log.success")));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    /** Patients upload for records they own; doctors for patients they treat; other staff for any patient. */
    private void checkCanUpload(Patient patient) {
        boolean allowed;
        if (currentUser.isPatient()) {
            allowed = patient.getOwner() != null && patient.getOwner().getId().equals(currentUser.id());
        } else if (currentUser.isDoctor()) {
            allowed = appointmentRepository.existsByDoctor_IdAndPatient_Id(currentUser.doctorId(), patient.getId());
        } else {
            allowed = true;
        }
        if (!allowed) {
            throw new NotFoundException(messageUtils.getMessage("patient.notfound"));
        }
    }

    /** The read rule (see the class comment). Admin access to details is handled by the callers. */
    private boolean canRead(MedicalRecord record) {
        if (isUploader(record)) {
            return true;
        }
        if (currentUser.isPatient()) {
            var owner = record.getPatient().getOwner();
            return owner != null && owner.getId().equals(currentUser.id());
        }
        return currentUser.isDoctor()
                && consentService.hasActiveConsent(record.getPatient().getId(), currentUser.doctorId());
    }

    private boolean isUploader(MedicalRecord record) {
        return record.getUploadedBy() != null && record.getUploadedBy().getId().equals(currentUser.id());
    }

    private void log(MedicalRecord record, Long userId, String action) {
        accessLogRepository.save(RecordAccessLog.builder()
                .record(record)
                .user(userRepository.getReferenceById(userId))
                .action(action)
                .accessedAt(Instant.now())
                .build());
    }

    /** Only PDF, JPEG and PNG, recognised by their first bytes ("magic numbers"). Null = not allowed. */
    private static String detectType(byte[] b) {
        if (b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') {
            return MediaType.APPLICATION_PDF_VALUE;
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        return null;
    }

    /** Display name only (never used as a path): drop any folder part and limit the length. */
    private static String cleanFilename(String name) {
        String cleaned = name == null ? "" : name.replaceAll(".*[/\\\\]", "").trim();
        if (cleaned.isEmpty()) {
            cleaned = "document";
        }
        return cleaned.length() > 255 ? cleaned.substring(cleaned.length() - 255) : cleaned;
    }

    private MedicalRecord findDetail(Long id) {
        return recordRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("record.notfound")));
    }
}
