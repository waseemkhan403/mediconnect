package com.mediconnect.security;

import com.mediconnect.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Who is calling, read from the validated access token. Only for endpoints that require authentication. */
@Component
public class CurrentUser {

    public Long id() {
        return Long.valueOf(jwt().getSubject());
    }

    public String role() {
        return jwt().getClaimAsString(TokenService.CLAIM_ROLE);
    }

    public boolean isPatient() {
        return User.ROLE_PATIENT.equals(role());
    }

    public boolean isDoctor() {
        return "DOCTOR".equals(role());
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role());
    }

    /** The caller's staff row id, or null for patient logins. */
    public Long staffId() {
        Object value = jwt().getClaim(TokenService.CLAIM_STAFF_ID);
        return value instanceof Number n ? n.longValue() : null;
    }

    /** The caller's doctor profile id, or null if the caller is not a doctor. */
    public Long doctorId() {
        Object value = jwt().getClaim(TokenService.CLAIM_DOCTOR_ID);
        return value instanceof Number n ? n.longValue() : null;
    }

    private Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        throw new IllegalStateException("No authenticated user on this request");
    }
}
