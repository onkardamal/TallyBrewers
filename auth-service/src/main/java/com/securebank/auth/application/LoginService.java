package com.securebank.auth.application;

import com.securebank.auth.domain.Passkey;
import com.securebank.auth.domain.Session;
import com.securebank.auth.domain.User;
import com.securebank.auth.domain.UserStatus;
import com.securebank.auth.infrastructure.jwt.JwtTokenProvider;
import com.securebank.auth.infrastructure.persistence.AuditLogRepository;
import com.securebank.auth.infrastructure.persistence.PasskeyRepository;
import com.securebank.auth.infrastructure.persistence.SessionRepository;
import com.securebank.auth.infrastructure.persistence.UserRepository;
import com.securebank.auth.infrastructure.webauthn.ChallengeStore;
import com.securebank.auth.infrastructure.webauthn.JpaCredentialRepository;
import com.securebank.auth.config.SecureBankProperties;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.exception.AssertionFailedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class LoginService {

    public record StartResponse(String handle, String assertionOptionsJson) {
    }

    public record LoginResult(String accessToken, String refreshToken, User user) {
    }

    /**
     * Outcome of a passkey assertion: either a completed login (session issued)
     * or a step-up requirement when the device is new to the account.
     */
    public record VerifyOutcome(boolean stepUpRequired, String stepUpHandle, LoginResult login) {
        static VerifyOutcome completed(LoginResult login) {
            return new VerifyOutcome(false, null, login);
        }

        static VerifyOutcome stepUp(String handle) {
            return new VerifyOutcome(true, handle, null);
        }
    }

    private final RelyingParty relyingParty;
    private final ChallengeStore<AssertionRequest> challengeStore;
    private final UserRepository userRepository;
    private final PasskeyRepository passkeyRepository;
    private final SessionRepository sessionRepository;
    private final TokenHasher tokenHasher;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecureBankProperties properties;
    private final AuditService auditService;
    private final AuditLogRepository auditLogRepository;
    private final StepUpChallengeStore stepUpChallengeStore;
    private final EmailSender emailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    public LoginService(RelyingParty relyingParty,
                        ChallengeStore<AssertionRequest> challengeStore,
                        UserRepository userRepository,
                        PasskeyRepository passkeyRepository,
                        SessionRepository sessionRepository,
                        TokenHasher tokenHasher,
                        JwtTokenProvider jwtTokenProvider,
                        SecureBankProperties properties,
                        AuditService auditService,
                        AuditLogRepository auditLogRepository,
                        StepUpChallengeStore stepUpChallengeStore,
                        EmailSender emailSender) {
        this.relyingParty = relyingParty;
        this.challengeStore = challengeStore;
        this.userRepository = userRepository;
        this.passkeyRepository = passkeyRepository;
        this.sessionRepository = sessionRepository;
        this.tokenHasher = tokenHasher;
        this.jwtTokenProvider = jwtTokenProvider;
        this.properties = properties;
        this.auditService = auditService;
        this.auditLogRepository = auditLogRepository;
        this.stepUpChallengeStore = stepUpChallengeStore;
        this.emailSender = emailSender;
    }

    /**
     * Start the login (WebAuthn assertion) ceremony.
     */
    @Transactional(readOnly = true)
    public StartResponse start(String email) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> AuthException.notFound("Account not found."));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw AuthException.badRequest("Account is not active.");
        }

        AssertionRequest request = relyingParty.startAssertion(
                StartAssertionOptions.builder()
                        .username(Optional.of(user.getEmail()))
                        .userHandle(Optional.of(JpaCredentialRepository.userHandleToByteArray(user.getWebauthnUserHandle())))
                        .build()
        );

        String handle = challengeStore.store(request);

        try {
            return new StartResponse(handle, request.toCredentialsGetJson());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize assertion options", e);
        }
    }

    /**
     * Finish the login ceremony by verifying the signed challenge.
     */
    @Transactional
    public VerifyOutcome verify(String handle, String credentialJson, String ipAddress, String userAgent) {
        AssertionRequest request = challengeStore.consume(handle)
                .orElseThrow(() -> AuthException.badRequest("Authentication session expired. Please try again."));

        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc;
        try {
            pkc = PublicKeyCredential.parseAssertionResponseJson(credentialJson);
        } catch (IOException e) {
            throw AuthException.badRequest("Malformed assertion response.");
        }

        AssertionResult result;
        try {
            result = relyingParty.finishAssertion(FinishAssertionOptions.builder()
                    .request(request)
                    .response(pkc)
                    .build());
        } catch (AssertionFailedException e) {
            e.printStackTrace();
            throw AuthException.badRequest("Assertion failed verification.");
        }

        if (!result.isSuccess()) {
            throw AuthException.badRequest("Assertion failed verification.");
        }

        // Retrieve user
        String email = request.getUsername()
                .orElseGet(() -> result.getUsername());
        if (email == null || email.isBlank()) {
            throw AuthException.badRequest("No email in assertion request.");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> AuthException.badRequest("Account not found."));

        // Update the passkey's counter and lastUsed timestamp
        String credentialId = pkc.getId().getBase64Url();
        Passkey passkey = passkeyRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> AuthException.badRequest("Passkey not found."));
        passkey.setCounter(result.getSignatureCount());
        passkey.setLastUsed(Instant.now());
        passkeyRepository.save(passkey);

        // New-device detection: if this account has never produced an audit
        // event from this device (user-agent), hold session issuance behind an
        // emailed step-up code. This is deterministic device recognition based
        // on real history — not a heuristic risk score.
        boolean knownDevice = userAgent != null && !userAgent.isBlank()
                && auditLogRepository.existsByUserIdAndDevice(user.getId(), userAgent);

        if (!knownDevice) {
            String code = generateNumericCode();
            String stepUpHandle = stepUpChallengeStore.store(
                    user.getId(), tokenHasher.hash(code), ipAddress, userAgent);
            emailSender.sendStepUpCode(user.getEmail(), user.getName(), code);
            auditService.record(user.getId(), "LOGIN_STEP_UP_REQUIRED", ipAddress, userAgent);
            return VerifyOutcome.stepUp(stepUpHandle);
        }

        // Generate session tokens
        LoginResult loginResult = createSessionForUser(user, ipAddress, userAgent);

        auditService.record(user.getId(), "LOGIN_SUCCESS", ipAddress, userAgent);

        return VerifyOutcome.completed(loginResult);
    }

    /**
     * Complete a step-up challenge for a new-device login by verifying the
     * emailed one-time code, then issue the session. Recording LOGIN_SUCCESS
     * here means the device becomes trusted for subsequent logins.
     */
    @Transactional
    public LoginResult completeStepUp(String handle, String code, String ipAddress, String userAgent) {
        StepUpChallengeStore.Challenge challenge = stepUpChallengeStore.peek(handle)
                .orElseThrow(() -> AuthException.badRequest(
                        "Verification session expired. Please sign in again."));

        if (code == null || !tokenHasher.hash(code).equals(challenge.codeHash())) {
            stepUpChallengeStore.recordFailedAttempt(handle);
            auditService.record(challenge.userId(), "LOGIN_STEP_UP_FAILED", ipAddress, userAgent);
            throw AuthException.badRequest("Incorrect or expired verification code.");
        }

        stepUpChallengeStore.consume(handle);

        User user = userRepository.findById(challenge.userId())
                .orElseThrow(() -> AuthException.unauthorized("User not found."));

        LoginResult loginResult = createSessionForUser(user, ipAddress, userAgent);
        auditService.record(user.getId(), "LOGIN_SUCCESS", ipAddress, userAgent);
        auditService.record(user.getId(), "LOGIN_STEP_UP_SUCCESS", ipAddress, userAgent);

        return loginResult;
    }

    private String generateNumericCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    /**
     * Verify a WebAuthn assertion solely to confirm the caller physically
     * controls a passkey belonging to {@code expectedUserId} — a fresh
     * biometric "step-up" — WITHOUT issuing any session. Used to require a live
     * passkey tap when approving a cross-device (QR) sign-in.
     *
     * @return true if the signature is valid AND the passkey belongs to the
     *         expected user.
     */
    @Transactional
    public boolean verifyAssertionForUser(String handle, String credentialJson, Long expectedUserId) {
        AssertionRequest request = challengeStore.consume(handle)
                .orElseThrow(() -> AuthException.badRequest("Verification expired. Please try again."));

        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc;
        try {
            pkc = PublicKeyCredential.parseAssertionResponseJson(credentialJson);
        } catch (IOException e) {
            throw AuthException.badRequest("Malformed assertion response.");
        }

        AssertionResult result;
        try {
            result = relyingParty.finishAssertion(FinishAssertionOptions.builder()
                    .request(request)
                    .response(pkc)
                    .build());
        } catch (AssertionFailedException e) {
            throw AuthException.badRequest("Assertion failed verification.");
        }
        if (!result.isSuccess()) {
            return false;
        }

        String credentialId = pkc.getId().getBase64Url();
        Passkey passkey = passkeyRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> AuthException.badRequest("Passkey not found."));
        passkey.setCounter(result.getSignatureCount());
        passkey.setLastUsed(Instant.now());
        passkeyRepository.save(passkey);

        // The signed challenge must have come from a passkey owned by the
        // authenticated approver — not just any valid passkey.
        return passkey.getUserId().equals(expectedUserId);
    }

    /**
     * Refresh the session using a rotating refresh token.
     */
    @Transactional
    public LoginResult refresh(String refreshToken, String ipAddress, String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw AuthException.unauthorized("Missing refresh token.");
        }

        String hash = tokenHasher.hash(refreshToken);
        Session session = sessionRepository.findByRefreshTokenHash(hash)
                .orElseThrow(() -> AuthException.unauthorized("Invalid or expired session."));

        if (session.isExpired()) {
            sessionRepository.delete(session);
            throw AuthException.unauthorized("Session expired.");
        }

        User user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> AuthException.unauthorized("User not found."));

        // Refresh token rotation: revoke old token by deleting the current session
        sessionRepository.delete(session);

        // Create a new session with a fresh refresh token
        LoginResult newSession = createSessionForUser(user, ipAddress, userAgent);

        auditService.record(user.getId(), "SESSION_REFRESHED", ipAddress, userAgent);

        return newSession;
    }

    /**
     * Log out by deleting the session.
     */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            String hash = tokenHasher.hash(refreshToken);
            sessionRepository.findByRefreshTokenHash(hash)
                    .ifPresent(session -> {
                        sessionRepository.delete(session);
                        auditService.record(session.getUserId(), "LOGOUT", session.getIpAddress(), session.getUserAgent());
                    });
        }
    }

    public LoginResult createSessionForUser(User user, String ipAddress, String userAgent) {
        String rawRefreshToken = tokenHasher.generateToken();
        String hash = tokenHasher.hash(rawRefreshToken);
        Instant expiresAt = Instant.now().plus(properties.getJwt().getRefreshTokenTtlDays(), ChronoUnit.DAYS);

        Session session = new Session(user.getId(), hash, ipAddress, userAgent, expiresAt);
        sessionRepository.save(session);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getName());

        return new LoginResult(accessToken, rawRefreshToken, user);
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
