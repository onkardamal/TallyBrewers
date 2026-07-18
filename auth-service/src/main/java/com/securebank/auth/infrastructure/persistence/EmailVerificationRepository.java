package com.securebank.auth.infrastructure.persistence;

import com.securebank.auth.domain.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findByTokenHash(String tokenHash);

    /**
     * Invalidate (delete) all not-yet-verified tokens for a user. Used by the
     * resend flow so that a previously emailed token can no longer be used
     * once a new one is issued.
     */
    @Modifying
    @Query("DELETE FROM EmailVerification ev WHERE ev.userId = :userId AND ev.verified = false")
    void deleteUnverifiedByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM EmailVerification ev WHERE ev.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") java.time.Instant now);
}
