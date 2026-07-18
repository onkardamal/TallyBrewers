package com.securebank.auth.application;

import com.securebank.auth.domain.EmailVerification;
import com.securebank.auth.domain.User;
import com.securebank.auth.domain.UserStatus;
import com.securebank.auth.infrastructure.persistence.EmailVerificationRepository;
import com.securebank.auth.infrastructure.persistence.UserRepository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies an email-verification token.
 *
 * The incoming raw token is SHA-256 hashed and looked up by hash. A token is
 * rejected if unknown, expired, or already used. On success the token is
 * marked verified (single-use) and the user's status advances to VERIFIED.
 */
@Service
public class EmailVerificationService {

    private final EmailVerificationRepository emailVerificationRepository;
    private final UserRepository userRepository;
    private final TokenHasher tokenHasher;
    private final AuditService auditService;

    public EmailVerificationService(EmailVerificationRepository emailVerificationRepository,
                                    UserRepository userRepository,
                                    TokenHasher tokenHasher,
                                    AuditService auditService) {
        this.emailVerificationRepository = emailVerificationRepository;
        this.userRepository = userRepository;
        this.tokenHasher = tokenHasher;
        this.auditService = auditService;
    }

    @Transactional
    public void verify(String rawToken, String ipAddress, String device) {
        if (rawToken == null || rawToken.isBlank()) {
            throw AuthException.badRequest("Invalid or expired verification link.");
        }

        String tokenHash = tokenHasher.hash(rawToken);
        Optional<EmailVerification> maybeVerification =
                emailVerificationRepository.findByTokenHash(tokenHash);

        if (maybeVerification.isEmpty()) {
            auditService.record(null, "VERIFY_EMAIL_UNKNOWN_TOKEN", ipAddress, device);
            throw AuthException.badRequest("Invalid or expired verification link.");
        }

        EmailVerification verification = maybeVerification.get();

        if (verification.isVerified()) {
            auditService.record(verification.getUserId(), "VERIFY_EMAIL_ALREADY_USED", ipAddress, device);
            throw AuthException.badRequest("Invalid or expired verification link.");
        }

        if (verification.isExpired(Instant.now())) {
            auditService.record(verification.getUserId(), "VERIFY_EMAIL_EXPIRED", ipAddress, device);
            throw AuthException.badRequest("Invalid or expired verification link.");
        }

        verification.markVerified();
        emailVerificationRepository.save(verification);

        User user = userRepository.findById(verification.getUserId())
                .orElseThrow(() -> AuthException.badRequest("Invalid or expired verification link."));

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.VERIFIED);
            userRepository.save(user);
        }

        auditService.record(user.getId(), "VERIFY_EMAIL_SUCCESS", ipAddress, device);
    }
}
