package com.mediconnect.controller;

import com.mediconnect.dto.ActiveStatusRequest;
import com.mediconnect.dto.user.PasswordRequest;
import com.mediconnect.dto.user.UserFiltersRequest;
import com.mediconnect.dto.user.UserRequest;
import com.mediconnect.helper.PageableBuilder;
import com.mediconnect.security.Access;
import com.mediconnect.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Logins (admin only). There is no plain PUT /users/{id}: a login has nothing else to edit
 * (email, name and role come from the staff row), so updates are the password and the status.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@PreAuthorize(Access.ADMIN)
public class UserController {

    private static final Map<String, String> SORT_FIELDS = Map.of("id", "id");

    private final UserService userService;
    private final PageableBuilder pageableBuilder;

    /** POST /users/list?page=0&size=10   Body (optional): { "role": "DOCTOR", "active": true } */
    @PostMapping("/list")
    public ResponseEntity<Object> getUsers(
            @RequestBody(required = false) UserFiltersRequest filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String[] sort) {
        return userService.getUsers(filters, pageableBuilder.build(page, size, sort, SORT_FIELDS));
    }

    /** GET /users/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Object> getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    /** POST /users   Body: { "staffId": 3, "password": "..." } : login for an existing staff member. */
    @PostMapping
    public ResponseEntity<Object> addUser(@Valid @RequestBody UserRequest request) {
        return userService.addUser(request);
    }

    /** PUT /users/{id}/password   Body: { "password": "..." } */
    @PutMapping("/{id}/password")
    public ResponseEntity<Object> updatePassword(
            @PathVariable Long id,
            @Valid @RequestBody PasswordRequest request) {
        return userService.updatePassword(id, request);
    }

    /** PUT /users/{id}/status   Body: { "active": false } */
    @PutMapping("/{id}/status")
    public ResponseEntity<Object> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody ActiveStatusRequest request) {
        return userService.updateUserStatus(id, request);
    }

    /** DELETE /users/{id} : removes the login, not the staff member or patient records. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteUser(@PathVariable Long id) {
        return userService.deleteUser(id);
    }
}
