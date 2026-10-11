package com.mediconnect.dto.auth;

/**
 * Returned by sign-in and refresh. Send accessToken as "Authorization: Bearer ..." on every request.
 * The refresh token is NOT here: it is set as an httpOnly cookie, so page scripts cannot read or steal it.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        MeResponse user
) {
}
