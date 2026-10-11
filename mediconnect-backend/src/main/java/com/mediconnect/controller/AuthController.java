package com.mediconnect.controller;

import com.mediconnect.dto.auth.ChangePasswordRequest;
import com.mediconnect.dto.auth.LoginRequest;
import com.mediconnect.dto.auth.OtpRequest;
import com.mediconnect.dto.auth.OtpVerifyRequest;
import com.mediconnect.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Sign-in and session endpoints. login, otp, refresh and logout are open (SecurityConfig);
 * me and password need a valid access token.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** POST /auth/login   Body: { "email": "admin@mediconnect.local", "password": "..." }   (staff) */
    @PostMapping("/login")
    public ResponseEntity<Object> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** POST /auth/otp/request   Body: { "phone": "9876543210" }   (patients; the code is printed in the server log) */
    @PostMapping("/otp/request")
    public ResponseEntity<Object> requestOtp(@Valid @RequestBody OtpRequest request) {
        return authService.requestOtp(request);
    }

    /** POST /auth/otp/verify   Body: { "phone": "9876543210", "otp": "123456" } */
    @PostMapping("/otp/verify")
    public ResponseEntity<Object> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return authService.verifyOtp(request);
    }

    /** POST /auth/refresh : uses the refresh_token cookie, returns a new access token and rotates the cookie. */
    @PostMapping("/refresh")
    public ResponseEntity<Object> refresh(
            @CookieValue(name = AuthService.REFRESH_COOKIE, required = false) String refreshToken) {
        return authService.refresh(refreshToken);
    }

    /** POST /auth/logout : revokes this device's refresh token and clears the cookie. */
    @PostMapping("/logout")
    public ResponseEntity<Object> logout(
            @CookieValue(name = AuthService.REFRESH_COOKIE, required = false) String refreshToken) {
        return authService.logout(refreshToken);
    }

    /** GET /auth/me */
    @GetMapping("/me")
    public ResponseEntity<Object> me() {
        return authService.me();
    }

    /** PUT /auth/password   Body: { "currentPassword": "...", "newPassword": "..." }   (staff) */
    @PutMapping("/password")
    public ResponseEntity<Object> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(request);
    }
}
