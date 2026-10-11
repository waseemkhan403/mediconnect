package com.mediconnect.security;

import com.mediconnect.entity.Staff;
import com.mediconnect.entity.User;
import com.mediconnect.repository.StaffRepository;
import com.mediconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Solves "who creates the first admin": on startup, if no ADMIN login exists, create one (staff row + login).
 * Email: app.admin.email (ADMIN_EMAIL). Password: app.admin.password (ADMIN_PASSWORD); when not set, a random one
 * is generated and printed once in the log. Change it after signing in (PUT /auth/password).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@mediconnect.local}")
    private String email;

    @Value("${app.admin.password:}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByStaff_StaffType("ADMIN")) {
            return;
        }
        String adminEmail = email.trim().toLowerCase();
        Optional<Staff> existing = staffRepository.findByEmailIgnoreCase(adminEmail);
        if (existing.isPresent() && !"ADMIN".equals(existing.get().getStaffType())) {
            log.error("Cannot create the first admin: {} belongs to a non-admin staff member. Set ADMIN_EMAIL.", adminEmail);
            return;
        }
        Staff staff = existing.orElseGet(() -> staffRepository.save(Staff.builder()
                .staffCode("ADMIN")
                .fullName("System Administrator")
                .gender("OTHER")
                .phone("0000000000")
                .email(adminEmail)
                .staffType("ADMIN")
                .status("ACTIVE")
                .build()));

        boolean generated = password.isBlank();
        String rawPassword = generated ? randomPassword() : password;
        userRepository.save(User.builder()
                .staff(staff)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .active(true)
                .build());
        if (generated) {
            log.warn("Created the first admin login: {} / {}  (generated; shown only once, change it after signing in)",
                    adminEmail, rawPassword);
        } else {
            log.info("Created the first admin login: {} (password from ADMIN_PASSWORD)", adminEmail);
        }
    }

    private static String randomPassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
