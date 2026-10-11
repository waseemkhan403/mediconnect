package com.mediconnect.service;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.dto.ActiveStatusRequest;
import com.mediconnect.dto.user.PasswordRequest;
import com.mediconnect.dto.user.UserFiltersRequest;
import com.mediconnect.dto.user.UserRequest;
import com.mediconnect.dto.user.UserResponse;
import com.mediconnect.entity.Staff;
import com.mediconnect.entity.User;
import com.mediconnect.exception.AlreadyExistException;
import com.mediconnect.exception.BadRequestException;
import com.mediconnect.exception.NotFoundException;
import com.mediconnect.repository.RefreshTokenRepository;
import com.mediconnect.repository.StaffRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin management of logins. Staff logins are created here for existing staff rows; patient logins create
 * themselves at first sign-in. The admin cannot deactivate or delete their own login (no self lock-out).
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final MessageUtils messageUtils;

    @Transactional(readOnly = true)
    public ResponseEntity<Object> getUsers(UserFiltersRequest filters, Pageable pageable) {
        if (filters == null) {
            filters = new UserFiltersRequest(null, null);
        }
        Page<UserResponse> page = userRepository.findAllWithFilters(
                        Texts.clean(filters.role()), filters.active(), pageable)
                .map(UserResponse::from);
        return ResponseHandler.generatePageResponse(
                HttpStatus.OK,
                PageResponse.from(page),
                new Status(200, messageUtils.getMessage("user.list.success")));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Object> getUserById(Long id) {
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                UserResponse.from(find(id)),
                new Status(200, messageUtils.getMessage("user.get.success", String.valueOf(id))));
    }

    @Transactional
    public ResponseEntity<Object> addUser(UserRequest request) {
        Staff staff = staffRepository.findById(request.staffId())
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("staff.notfound")));
        // OTHER is a catch-all staff type with no defined permissions, so it gets no login.
        if ("OTHER".equals(staff.getStaffType())) {
            throw new BadRequestException(messageUtils.getMessage("user.staff.norole"));
        }
        if (userRepository.existsByStaffId(staff.getId())) {
            throw new AlreadyExistException(messageUtils.getMessage("user.staff.exists"));
        }
        User saved = userRepository.save(User.builder()
                .staff(staff)
                .passwordHash(passwordEncoder.encode(request.password()))
                .active(true)
                .build());
        return ResponseHandler.generateResponse(
                HttpStatus.CREATED,
                UserResponse.from(saved),
                new Status(201, messageUtils.getMessage("user.add.success")));
    }

    /** Admin password reset. Signs the user out everywhere. */
    @Transactional
    public ResponseEntity<Object> updatePassword(Long id, PasswordRequest request) {
        User user = find(id);
        if (user.getStaff() == null) {
            throw new BadRequestException(messageUtils.getMessage("auth.password.patient"));
        }
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        refreshTokenRepository.deleteAllForUser(user.getId());
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                UserResponse.from(user),
                new Status(200, messageUtils.getMessage("user.password.success")));
    }

    /** Deactivating also ends every session (current access tokens still expire within 15 minutes). */
    @Transactional
    public ResponseEntity<Object> updateUserStatus(Long id, ActiveStatusRequest request) {
        User user = find(id);
        refuseSelf(user);
        user.setActive(request.active());
        userRepository.save(user);
        if (!request.active()) {
            refreshTokenRepository.deleteAllForUser(user.getId());
        }
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                UserResponse.from(user),
                new Status(200, messageUtils.getMessage("user.status.success")));
    }

    /** Removes the login only; the staff row or patient records stay (patients' owner becomes null). */
    @Transactional
    public ResponseEntity<Object> deleteUser(Long id) {
        User user = find(id);
        refuseSelf(user);
        userRepository.delete(user);
        userRepository.flush();
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                null,
                new Status(200, messageUtils.getMessage("user.delete.success")));
    }

    private void refuseSelf(User user) {
        if (user.getId().equals(currentUser.id())) {
            throw new BadRequestException(messageUtils.getMessage("user.self"));
        }
    }

    private User find(Long id) {
        return userRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("user.notfound")));
    }
}
