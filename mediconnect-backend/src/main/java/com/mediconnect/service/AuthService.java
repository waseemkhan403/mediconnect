package com.mediconnect.service;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.dto.auth.AuthResponse;
import com.mediconnect.dto.auth.ChangePasswordRequest;
import com.mediconnect.dto.auth.LoginRequest;
import com.mediconnect.dto.auth.MeResponse;
import com.mediconnect.dto.auth.OtpRequest;
import com.mediconnect.dto.auth.OtpVerifyRequest;
import com.mediconnect.entity.RefreshToken;
import com.mediconnect.entity.User;
import com.mediconnect.exception.BadRequestException;
import com.mediconnect.exception.NotFoundException;
import com.mediconnect.exception.UnauthorizedException;
import com.mediconnect.repository.PatientRepository;
import com.mediconnect.repository.RefreshTokenRepository;
import com.mediconnect.repository.UserRepository;
import com.mediconnect.response.ResponseHandler;
import com.mediconnect.response.Status;
import com.mediconnect.security.CurrentUser;
import com.mediconnect.security.OtpService;
import com.mediconnect.security.TokenService;
import com.mediconnect.util.Hashes;
import com.mediconnect.util.Texts;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Sign-in for both kinds of user, and the session lifecycle:
 *  - staff: email + password. Patients: phone + one-time code (the patient login is created on first use).
 *  - every sign-in returns a 15-minute access token in the body and a 7-day refresh token in an httpOnly cookie.
 *  - refresh rotates the refresh token. Presenting an already-rotated token means a copy exists somewhere else,
 *    so every session of that user is ended (refresh-token reuse detection).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String REFRESH_COOKIE = "refresh_token";
    static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final String COOKIE_PATH = "/auth";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PatientRepository patientRepository;
    private final TokenService tokenService;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final MessageUtils messageUtils;
    private final SecureRandom random = new SecureRandom();

    /** true in production (HTTPS only). Off for local http testing, e.g. Postman against http://localhost. */
    @Value("${app.auth.refresh-cookie-secure:true}")
    private boolean cookieSecure;

    // -------------------------------------------------------------------------
    // STAFF SIGN-IN
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> login(LoginRequest request) {
        // One message for "no such email" and "wrong password", so the API does not reveal which emails exist.
        User user = userRepository.findByStaffEmail(Texts.email(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedException(messageUtils.getMessage("auth.login.invalid")));
        checkCanSignIn(user);
        return issueTokens(user, "auth.login.success");
    }

    // -------------------------------------------------------------------------
    // PATIENT SIGN-IN (one-time code)
    // -------------------------------------------------------------------------
    public ResponseEntity<Object> requestOtp(OtpRequest request) {
        otpService.send(Texts.phone(request.phone()));
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                null,
                new Status(200, messageUtils.getMessage("auth.otp.sent")));
    }

    @Transactional
    public ResponseEntity<Object> verifyOtp(OtpVerifyRequest request) {
        String phone = Texts.phone(request.phone());
        if (!otpService.verify(phone, request.otp())) {
            throw new UnauthorizedException(messageUtils.getMessage("auth.otp.invalid"));
        }
        User user = userRepository.findByPhone(phone)
                .orElseGet(() -> userRepository.save(User.builder().phone(phone).active(true).build()));
        checkCanSignIn(user);
        // Walk-in records registered at reception under this phone now belong to this login.
        patientRepository.claimUnownedByPhone(phone, user);
        return issueTokens(user, "auth.login.success");
    }

    // -------------------------------------------------------------------------
    // REFRESH (rotates the refresh token)
    // -------------------------------------------------------------------------
    /** noRollbackFor: the "sign out everywhere" on reuse must be committed even though the request is refused. */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public ResponseEntity<Object> refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException(messageUtils.getMessage("auth.refresh.invalid"));
        }
        RefreshToken token = refreshTokenRepository.findByTokenHash(Hashes.sha256(rawToken))
                .orElseThrow(() -> new UnauthorizedException(messageUtils.getMessage("auth.refresh.invalid")));
        User user = token.getUser();
        if (token.isRotated()) {
            refreshTokenRepository.deleteAllForUser(user.getId());
            throw new UnauthorizedException(messageUtils.getMessage("auth.refresh.reused"));
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException(messageUtils.getMessage("auth.refresh.invalid"));
        }
        token.setRotated(true);
        refreshTokenRepository.save(token);
        checkCanSignIn(user);
        return issueTokens(user, "auth.refresh.success");
    }

    // -------------------------------------------------------------------------
    // LOGOUT (this device only)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            refreshTokenRepository.findByTokenHash(Hashes.sha256(rawToken)).ifPresent(refreshTokenRepository::delete);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString());
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                null,
                new Status(200, messageUtils.getMessage("auth.logout.success")),
                headers);
    }

    // -------------------------------------------------------------------------
    // ME
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> me() {
        User user = loadCurrentUser();
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                MeResponse.from(user, tokenService.doctorIdOf(user)),
                new Status(200, messageUtils.getMessage("auth.me.success")));
    }

    // -------------------------------------------------------------------------
    // CHANGE OWN PASSWORD (staff): signs out every other session
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> changePassword(ChangePasswordRequest request) {
        User user = loadCurrentUser();
        if (user.getStaff() == null) {
            throw new BadRequestException(messageUtils.getMessage("auth.password.patient"));
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException(messageUtils.getMessage("auth.password.wrong"));
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenRepository.deleteAllForUser(user.getId());
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                null,
                new Status(200, messageUtils.getMessage("auth.password.success")));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private void checkCanSignIn(User user) {
        boolean staffInactive = user.getStaff() != null && !"ACTIVE".equals(user.getStaff().getStatus());
        if (!user.isActive() || staffInactive) {
            throw new UnauthorizedException(messageUtils.getMessage("auth.account.disabled"));
        }
    }

    private ResponseEntity<Object> issueTokens(User user, String messageKey) {
        String accessToken = tokenService.createAccessToken(user);
        refreshTokenRepository.deleteExpiredForUser(user.getId(), Instant.now());

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(Hashes.sha256(refreshToken))
                .expiresAt(Instant.now().plus(REFRESH_TOKEN_TTL))
                .build());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken, REFRESH_TOKEN_TTL).toString());
        AuthResponse body = new AuthResponse(
                accessToken, "Bearer", TokenService.ACCESS_TOKEN_TTL.toSeconds(),
                MeResponse.from(user, tokenService.doctorIdOf(user)));
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                body,
                new Status(200, messageUtils.getMessage(messageKey)),
                headers);
    }

    /** httpOnly: invisible to page scripts. SameSite=Strict + path /auth: only sent to the auth endpoints. */
    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    private User loadCurrentUser() {
        return userRepository.findDetailById(currentUser.id())
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("user.notfound")));
    }
}
