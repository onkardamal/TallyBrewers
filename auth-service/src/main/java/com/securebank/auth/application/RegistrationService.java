package com.securebank.auth.application;

import com.securebank.auth.domain.EmailVerification;
import com.securebank.auth.domain.User;
import com.securebank.auth.infrastructure.persistence.EmailVerificationRepository;
import com.securebank.auth.infrastructure.persistence.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration and issuance/resend of email-verification tokens.
 *
 * Registration creates a PENDING_VERIFICATION user, generates a random
 * verification token, stores only its SHA-256 hash, and emails the raw token.
 * The raw token is never persisted and never returned in an API response.
 *
 * To avoid account enumeration, the public-facing register and resend flows
 * return success without revealing whether an email already exists; details
 * are only surfaced through the (rate-limited) email channel.
 */
@Service
public class RegistrationService {

    static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final TokenHasher tokenHasher;
    private final EmailSender emailSender;
    private final RateLimiter resendRateLimiter;
    private final AuditService auditService;

    public RegistrationService(UserRepository userRepository,
                               EmailVerificationRepository emailVerificationRepository,
                               TokenHasher tokenHasher,
                               EmailSender emailSender,
                               RateLimiter resendRateLimiter,
                               AuditService auditService) {
        this.userRepository = userRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.tokenHasher = tokenHasher;
        this.emailSender = emailSender;
        this.resendRateLimiter = resendRateLimiter;
        this.auditService = auditService;
    }

    /**
     * Register a new user and send a verification email.
     *
     * If the email is already registered, no new account is created and no
     * error is exposed to the caller (enumeration protection); the request
     * simply completes.
     */
    @Transactional
    public void register(String name, String email, String phone, String ipAddress, String device) {
        String normalizedEmail = normalizeEmail(email);

        if (userRepository.existsByEmail(normalizedEmail)) {
            // Do not reveal existence. Nothing further to do.
            auditService.record(null, "REGISTER_DUPLICATE_EMAIL", ipAddress, device);
            return;
        }

        User user = userRepository.save(new User(name.trim(), normalizedEmail, normalizePhone(phone)));
        issueAndSendToken(user);
        auditService.record(user.getId(), "REGISTER", ipAddress, device);
    }

    /**
     * Resend a verification email. Invalidates any previously issued,
     * still-unverified tokens for the user before issuing a new one, so old
     * links stop working. Rate-limited per email. Always completes without
     * revealing whether the email exists or is already verified.
     */
    @Transactional
    public void resendVerification(String email, String ipAddress, String device) {
        String normalizedEmail = normalizeEmail(email);

        if (!resendRateLimiter.tryAcquire("resend:" + normalizedEmail)) {
            throw AuthException.tooManyRequests(
                    "Please wait a moment before requesting another verification email.");
        }

        Optional<User> maybeUser = userRepository.findByEmail(normalizedEmail);
        if (maybeUser.isEmpty()) {
            auditService.record(null, "VERIFY_RESEND_UNKNOWN_EMAIL", ipAddress, device);
            return;
        }

        User user = maybeUser.get();
        if (user.getStatus() != com.securebank.auth.domain.UserStatus.PENDING_VERIFICATION) {
            // Already verified — nothing to resend, but don't reveal that.
            auditService.record(user.getId(), "VERIFY_RESEND_ALREADY_VERIFIED", ipAddress, device);
            return;
        }

        // Invalidate prior unverified tokens so old links can no longer be used.
        emailVerificationRepository.deleteUnverifiedByUserId(user.getId());
        issueAndSendToken(user);
        auditService.record(user.getId(), "VERIFY_RESEND", ipAddress, device);
    }

    private void issueAndSendToken(User user) {
        String rawToken = tokenHasher.generateToken();
        String tokenHash = tokenHasher.hash(rawToken);
        Instant expiresAt = Instant.now().plus(TOKEN_TTL);

        emailVerificationRepository.save(new EmailVerification(user.getId(), tokenHash, expiresAt));
        emailSender.sendVerificationEmail(user.getEmail(), user.getName(), rawToken);
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String trimmed = phone.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
