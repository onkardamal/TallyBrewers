package com.securebank.auth.application;

import com.securebank.auth.domain.Passkey;
import com.securebank.auth.domain.Session;
import com.securebank.auth.domain.User;
import com.securebank.auth.domain.UserStatus;
import com.securebank.auth.infrastructure.jwt.JwtTokenProvider;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class LoginService {

    public record StartResponse(String handle, String assertionOptionsJson) {
    }

    public record LoginResult(String accessToken, String refreshToken, User user) {
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

    public LoginService(RelyingParty relyingParty,
                        ChallengeStore<AssertionRequest> challengeStore,
                        UserRepository userRepository,
                        PasskeyRepository passkeyRepository,
                        SessionRepository sessionRepository,
                        TokenHasher tokenHasher,
                        JwtTokenProvider jwtTokenProvider,
                        SecureBankProperties properties,
                        AuditService auditService) {
        this.relyingParty = relyingParty;
        this.challengeStore = challengeStore;
        this.userRepository = userRepository;
        this.passkeyRepository = passkeyRepository;
        this.sessionRepository = sessionRepository;
        this.tokenHasher = tokenHasher;
        this.jwtTokenProvider = jwtTokenProvider;
        this.properties = properties;
        this.auditService = auditService;
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
    public LoginResult verify(String handle, String credentialJson, String ipAddress, String userAgent) {
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

        // Generate session tokens
        LoginResult loginResult = createSessionForUser(user, ipAddress, userAgent);

        auditService.record(user.getId(), "LOGIN_SUCCESS", ipAddress, userAgent);

        return loginResult;
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
