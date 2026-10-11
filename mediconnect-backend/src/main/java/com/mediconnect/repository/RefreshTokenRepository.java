package com.mediconnect.repository;

import com.mediconnect.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** With the user and staff loaded: a refresh issues a new access token, which needs the role. */
    @Query("""
            SELECT t FROM RefreshToken t
            JOIN FETCH t.user u
            LEFT JOIN FETCH u.staff
            WHERE t.tokenHash = :tokenHash
            """)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Signs the user out everywhere (deactivation, password change, or a stolen token being replayed). */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.user.id = :userId")
    void deleteAllForUser(Long userId);

    /** Housekeeping at sign-in, so rotated and expired rows do not pile up. */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.user.id = :userId AND t.expiresAt < :now")
    void deleteExpiredForUser(Long userId, Instant now);
}
