package com.mediconnect.security;

import com.mediconnect.entity.Doctor;
import com.mediconnect.entity.User;
import com.mediconnect.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Issues short-lived access tokens. Claims: sub = user id, role, and for staff the staffId (plus doctorId for
 * doctors) so ownership checks need no extra query. Because a JWT cannot be recalled, its lifetime is kept short;
 * deactivating a user stops new tokens at once (refresh is refused) and old ones within ACCESS_TOKEN_TTL.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_STAFF_ID = "staffId";
    public static final String CLAIM_DOCTOR_ID = "doctorId";
    private static final String ISSUER = "mediconnect";

    private final JwtEncoder jwtEncoder;
    private final DoctorRepository doctorRepository;

    /** Expects staff to be loaded for staff logins. */
    public String createAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_TTL))
                .claim(CLAIM_ROLE, user.role());
        if (user.getStaff() != null) {
            claims.claim(CLAIM_STAFF_ID, user.getStaff().getId());
        }
        Long doctorId = doctorIdOf(user);
        if (doctorId != null) {
            claims.claim(CLAIM_DOCTOR_ID, doctorId);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    /** The doctor profile behind a DOCTOR login, or null for everyone else. */
    public Long doctorIdOf(User user) {
        if (user.getStaff() == null || !"DOCTOR".equals(user.getStaff().getStaffType())) {
            return null;
        }
        return doctorRepository.findByStaffId(user.getStaff().getId()).map(Doctor::getId).orElse(null);
    }
}
