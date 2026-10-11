package com.mediconnect.controller;

import com.mediconnect.dto.chat.ChatAccessLogFiltersRequest;
import com.mediconnect.dto.chat.ChatMessageRequest;
import com.mediconnect.dto.chat.ChatThreadFiltersRequest;
import com.mediconnect.helper.PageableBuilder;
import com.mediconnect.security.Access;
import com.mediconnect.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Patient-doctor chat. Threads are opened by finalizing an encounter, so there is no create endpoint, and
 * messages are never edited or deleted. New messages are also pushed over WebSocket (/ws, /user/queue/chat).
 * Admins can read every chat and close one (safety oversight) but never write in it.
 */
@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
@PreAuthorize(Access.CHAT_VIEWERS)
public class ChatController {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "id", "id",
            "lastActivity", "lastActivityAt",
            "closesAt", "closesAt");

    private final ChatService chatService;
    private final PageableBuilder pageableBuilder;

    /** POST /chats/list?sort=lastActivity,desc   Body (optional): { "encounterId": 3, "patientId": 1, "doctorId": 2, "openOnly": true } */
    @PostMapping("/list")
    public ResponseEntity<Object> getThreads(
            @RequestBody(required = false) ChatThreadFiltersRequest filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "lastActivity,desc") String[] sort) {
        return chatService.getThreads(filters, pageableBuilder.build(page, size, sort, SORT_FIELDS));
    }

    /**
     * POST /chats/access-log?page=0&size=10   Body (optional): { "threadId": 3 }
     * Admin reads and closes of chats. Patients and doctors get the log of their own chats only.
     */
    @PostMapping("/access-log")
    public ResponseEntity<Object> getAccessLog(
            @RequestBody(required = false) ChatAccessLogFiltersRequest filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "accessedAt,desc") String[] sort) {
        return chatService.getAccessLog(filters,
                pageableBuilder.build(page, size, sort, Map.of("id", "id", "accessedAt", "accessedAt")));
    }

    /** POST /chats/{id}/messages   Body: { "body": "Should I take the tablet before food?" } */
    @PreAuthorize(Access.CHAT_PARTICIPANTS)
    @PostMapping("/{id}/messages")
    public ResponseEntity<Object> sendMessage(@PathVariable Long id, @Valid @RequestBody ChatMessageRequest request) {
        return chatService.sendMessage(id, request);
    }

    /** GET /chats/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Object> getThreadById(@PathVariable Long id) {
        return chatService.getThreadById(id);
    }

    /** GET /chats/{id}/messages?before=120&size=30 : latest messages; pass before = oldest id shown for older ones.
     *  An admin's read is written to the chat access log. */
    @GetMapping("/{id}/messages")
    public ResponseEntity<Object> getMessages(
            @PathVariable Long id,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "30") int size) {
        return chatService.getMessages(id, before, size);
    }

    /** PUT /chats/{id}/read : marks everything up to now as read for the caller's side. */
    @PreAuthorize(Access.CHAT_PARTICIPANTS)
    @PutMapping("/{id}/read")
    public ResponseEntity<Object> markRead(@PathVariable Long id) {
        return chatService.markRead(id);
    }

    /** PUT /chats/{id}/close : the doctor (or an admin, for safety) ends the conversation early. It stays readable. */
    @PreAuthorize(Access.ADMIN_OR_DOCTOR)
    @PutMapping("/{id}/close")
    public ResponseEntity<Object> closeThread(@PathVariable Long id) {
        return chatService.closeThread(id);
    }
}
