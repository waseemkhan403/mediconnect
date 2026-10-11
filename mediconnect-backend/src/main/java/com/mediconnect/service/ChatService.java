package com.mediconnect.service;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.dto.chat.ChatAccessLogFiltersRequest;
import com.mediconnect.dto.chat.ChatAccessLogResponse;
import com.mediconnect.dto.chat.ChatMessageRequest;
import com.mediconnect.dto.chat.ChatMessageResponse;
import com.mediconnect.dto.chat.ChatMessagesResponse;
import com.mediconnect.dto.chat.ChatThreadFiltersRequest;
import com.mediconnect.dto.chat.ChatThreadResponse;
import com.mediconnect.entity.Appointment;
import com.mediconnect.entity.ChatAccessLog;
import com.mediconnect.entity.ChatMessage;
import com.mediconnect.entity.ChatThread;
import com.mediconnect.entity.Encounter;
import com.mediconnect.entity.User;
import com.mediconnect.exception.BadRequestException;
import com.mediconnect.exception.NotFoundException;
import com.mediconnect.helper.HospitalZone;
import com.mediconnect.repository.ChatAccessLogRepository;
import com.mediconnect.repository.ChatMessageRepository;
import com.mediconnect.repository.ChatThreadRepository;
import com.mediconnect.repository.UserRepository;
import com.mediconnect.response.PageResponse;
import com.mediconnect.response.ResponseHandler;
import com.mediconnect.response.Status;
import com.mediconnect.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Patient-doctor chat after a consultation.
 *  - One thread per encounter, opened automatically when the doctor finalizes it, open for app.chat.window-days.
 *    The doctor may close it earlier. A closed thread stays readable but nobody can write to it.
 *  - Only two parties write: the patient login that owns the patient record, and the encounter's doctor.
 *    Admins can read every chat and close one (safety oversight), but never send or mark read. Nurses, reception
 *    and consenting doctors do not read chats; another patient or doctor gets 404.
 *  - Every admin read of messages and every admin close is logged (chat_access_logs); both parties can read it.
 *  - Messages are sent with REST (validated, saved, then pushed). WebSocket only delivers them; see ChatNotifier.
 *  - Messages are never edited or deleted.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    static final int DEFAULT_PAGE_SIZE = 30;
    static final int MAX_PAGE_SIZE = 100;

    private final ChatThreadRepository threadRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatAccessLogRepository accessLogRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final HospitalZone hospitalZone;
    private final CurrentUser currentUser;
    private final MessageUtils messageUtils;

    @Value("${app.chat.window-days:7}")
    private int windowDays;

    /** Called by EncounterService when an encounter becomes FINAL (same transaction). */
    @Transactional
    public void openThread(Encounter encounter) {
        if (threadRepository.existsByEncounter_Id(encounter.getId())) {
            return;
        }
        Instant openedAt = encounter.getFinalizedAt();
        threadRepository.save(ChatThread.builder()
                .encounter(encounter)
                .openedAt(openedAt)
                .closesAt(openedAt.plus(Duration.ofDays(windowDays)))
                .lastActivityAt(openedAt)
                .build());
    }

    // -------------------------------------------------------------------------
    // INBOX (the caller's own threads, with unread counts)
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getThreads(ChatThreadFiltersRequest filters, Pageable pageable) {
        if (filters == null) {
            filters = new ChatThreadFiltersRequest(null, null, null, null);
        }
        Instant now = Instant.now();
        // Forced server-side: a doctor sees their own threads, a patient login those of the patients it owns.
        // Admins see all threads.
        Long doctorId = currentUser.isDoctor() ? currentUser.doctorId()
                : currentUser.isAdmin() ? filters.doctorId() : null;
        Long ownerId = currentUser.isPatient() ? currentUser.id() : null;
        Page<ChatThread> threads = threadRepository.findAllWithFilters(
                filters.encounterId(), filters.patientId(), doctorId, ownerId,
                Boolean.TRUE.equals(filters.openOnly()), now, pageable);
        Map<Long, Long> unread = unreadCounts(threads.getContent().stream().map(ChatThread::getId).toList());
        ZoneId zone = hospitalZone.get();
        Page<ChatThreadResponse> page = threads.map(
                t -> ChatThreadResponse.from(t, unread.getOrDefault(t.getId(), 0L), zone, now));
        return ResponseHandler.generatePageResponse(
                HttpStatus.OK,
                PageResponse.from(page),
                new Status(200, messageUtils.getMessage("chat.list.success")));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Object> getThreadById(Long id) {
        ChatThread thread = findOwnThread(id);
        long unread = unreadCounts(List.of(id)).getOrDefault(id, 0L);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                ChatThreadResponse.from(thread, unread, hospitalZone.get(), Instant.now()),
                new Status(200, messageUtils.getMessage("chat.get.success", String.valueOf(id))));
    }

    // -------------------------------------------------------------------------
    // MESSAGES (newest page first, older pages with ?before=messageId)
    // -------------------------------------------------------------------------
    /** Not read-only: an admin reading messages writes an access log row. */
    @Transactional
    public ResponseEntity<Object> getMessages(Long id, Long before, int size) {
        ChatThread thread = findOwnThread(id);
        logAdminAccess(thread, "READ");
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        // One extra row tells whether older messages exist, without a COUNT query.
        List<ChatMessage> rows = messageRepository.findPage(id, before, PageRequest.of(0, safeSize + 1));
        boolean hasMore = rows.size() > safeSize;
        List<ChatMessage> pageRows = new ArrayList<>(hasMore ? rows.subList(0, safeSize) : rows);
        Collections.reverse(pageRows);
        String patientName = patientName(thread);
        String doctorName = doctorName(thread);
        List<ChatMessageResponse> messages = pageRows.stream()
                .map(m -> ChatMessageResponse.from(m, patientName, doctorName))
                .toList();
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                new ChatMessagesResponse(messages, hasMore),
                new Status(200, messageUtils.getMessage("chat.messages.success")));
    }

    // -------------------------------------------------------------------------
    // SEND: saved here, delivered over WebSocket after the transaction commits
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> sendMessage(Long id, ChatMessageRequest request) {
        ChatThread thread = findOwnThread(id);
        Instant now = Instant.now();
        if (!thread.isOpen(now)) {
            throw new BadRequestException(messageUtils.getMessage("chat.closed"));
        }
        String role = currentUser.isDoctor() ? "DOCTOR" : "PATIENT";
        ChatMessage message = messageRepository.save(ChatMessage.builder()
                .thread(thread)
                .sender(userRepository.getReferenceById(currentUser.id()))
                .senderRole(role)
                .body(request.body().trim())
                .sentAt(now)
                .build());
        thread.setLastActivityAt(now);
        // The sender has obviously seen everything up to their own message.
        if ("DOCTOR".equals(role)) {
            thread.setDoctorLastReadId(message.getId());
        } else {
            thread.setPatientLastReadId(message.getId());
        }
        threadRepository.save(thread);

        ChatMessageResponse response = ChatMessageResponse.from(message, patientName(thread), doctorName(thread));
        events.publishEvent(new ChatNotifier.MessageSent(participants(thread), response));
        return ResponseHandler.generateResponse(
                HttpStatus.CREATED,
                response,
                new Status(201, messageUtils.getMessage("chat.send.success")));
    }

    // -------------------------------------------------------------------------
    // MARK READ (everything up to the latest message, for the caller's side)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> markRead(Long id) {
        ChatThread thread = findOwnThread(id);
        long latest = messageRepository.findLatestId(id);
        if (currentUser.isDoctor()) {
            thread.setDoctorLastReadId(latest);
        } else {
            thread.setPatientLastReadId(latest);
        }
        ChatThread saved = threadRepository.save(thread);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                ChatThreadResponse.from(saved, 0, hospitalZone.get(), Instant.now()),
                new Status(200, messageUtils.getMessage("chat.read.success")));
    }

    // -------------------------------------------------------------------------
    // CLOSE (the doctor ends the conversation before the window runs out)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> closeThread(Long id) {
        ChatThread thread = findOwnThread(id);
        Instant now = Instant.now();
        if (!thread.isOpen(now)) {
            throw new BadRequestException(messageUtils.getMessage("chat.closed"));
        }
        thread.setClosedAt(now);
        thread.setClosedByRole(currentUser.isAdmin() ? "ADMIN" : "DOCTOR");
        ChatThread saved = threadRepository.save(thread);
        logAdminAccess(saved, "CLOSE");
        long unread = unreadCounts(List.of(id)).getOrDefault(id, 0L);
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                ChatThreadResponse.from(saved, unread, hospitalZone.get(), now),
                new Status(200, messageUtils.getMessage("chat.close.success")));
    }

    // -------------------------------------------------------------------------
    // ACCESS LOG (admin reads and closes; each party sees the log of their own chats)
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getAccessLog(ChatAccessLogFiltersRequest filters, Pageable pageable) {
        Long threadId = filters != null ? filters.threadId() : null;
        Long doctorId = currentUser.isDoctor() ? currentUser.doctorId() : null;
        Long ownerId = currentUser.isPatient() ? currentUser.id() : null;
        Page<ChatAccessLogResponse> page = accessLogRepository.findAllWithFilters(threadId, doctorId, ownerId, pageable)
                .map(ChatAccessLogResponse::from);
        return ResponseHandler.generatePageResponse(
                HttpStatus.OK,
                PageResponse.from(page),
                new Status(200, messageUtils.getMessage("chat.log.success")));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    /** Oversight is recorded: admins are not a party to the chat. The parties' own reads are not logged. */
    private void logAdminAccess(ChatThread thread, String action) {
        if (!currentUser.isAdmin()) {
            return;
        }
        accessLogRepository.save(ChatAccessLog.builder()
                .thread(thread)
                .user(userRepository.getReferenceById(currentUser.id()))
                .action(action)
                .accessedAt(Instant.now())
                .build());
    }

    /** The two parties (the encounter's doctor, the patient login owning the record), or an admin. */
    private ChatThread findOwnThread(Long id) {
        ChatThread thread = threadRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("chat.notfound")));
        Appointment appointment = thread.getEncounter().getAppointment();
        boolean allowed;
        if (currentUser.isAdmin()) {
            allowed = true;
        } else if (currentUser.isDoctor()) {
            allowed = appointment.getDoctor().getId().equals(currentUser.doctorId());
        } else if (currentUser.isPatient()) {
            User owner = appointment.getPatient().getOwner();
            allowed = owner != null && owner.getId().equals(currentUser.id());
        } else {
            allowed = false;
        }
        if (!allowed) {
            throw new NotFoundException(messageUtils.getMessage("chat.notfound"));
        }
        return thread;
    }

    /** Unread counts for the caller's side, by thread id (threads with none are absent). Admins have no side: 0. */
    private Map<Long, Long> unreadCounts(List<Long> threadIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (threadIds.isEmpty() || currentUser.isAdmin()) {
            return counts;
        }
        List<Object[]> rows = currentUser.isDoctor()
                ? messageRepository.countUnreadForDoctor(threadIds)
                : messageRepository.countUnreadForPatient(threadIds);
        for (Object[] row : rows) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    /** Login ids that receive the WebSocket push: the patient's owner login and the doctor's login. */
    private Set<Long> participants(ChatThread thread) {
        Appointment appointment = thread.getEncounter().getAppointment();
        Set<Long> userIds = new LinkedHashSet<>();
        if (appointment.getPatient().getOwner() != null) {
            userIds.add(appointment.getPatient().getOwner().getId());
        }
        userRepository.findIdByStaffId(appointment.getDoctor().getStaff().getId()).ifPresent(userIds::add);
        return userIds;
    }

    private static String patientName(ChatThread thread) {
        return thread.getEncounter().getAppointment().getPatient().getFullName();
    }

    private static String doctorName(ChatThread thread) {
        return thread.getEncounter().getAppointment().getDoctor().getStaff().getFullName();
    }
}
