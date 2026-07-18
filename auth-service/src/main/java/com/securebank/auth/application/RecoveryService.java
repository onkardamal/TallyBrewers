package com.securebank.auth.application;

import com.securebank.auth.domain.EmailVerification;
import com.securebank.auth.domain.Passkey;
import com.securebank.auth.domain.RecoveryCode;
import com.securebank.auth.domain.User;
import com.securebank.auth.domain.UserStatus;
import com.securebank.auth.infrastructure.persistence.EmailVerificationRepository;
import com.securebank.auth.infrastructure.persistence.PasskeyRepository;
import com.securebank.auth.infrastructure.persistence.RecoveryCodeRepository;
import com.securebank.auth.infrastructure.persistence.UserRepository;
import com.securebank.auth.infrastructure.webauthn.ChallengeStore;
import com.securebank.auth.infrastructure.webauthn.JpaCredentialRepository;
import com.securebank.auth.application.LoginService.LoginResult;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.RegistrationFailedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class RecoveryService {

    public record RecoveryVerifyResponse(String handle, String creationOptionsJson) {
    }

    public record RecoveryCompleteResult(String accessToken, String refreshToken, List<String> recoveryCodes) {
    }

    private static final Duration EMAIL_TOKEN_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final PasskeyRepository passkeyRepository;
    private final TokenHasher tokenHasher;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final RelyingParty relyingParty;
    private final ChallengeStore<PublicKeyCredentialCreationOptions> challengeStore;
    private final RecoveryCodeService recoveryCodeService;
    private final LoginService loginService;
    private final AuditService auditService;

    public RecoveryService(UserRepository userRepository,
                           EmailVerificationRepository emailVerificationRepository,
                           RecoveryCodeRepository recoveryCodeRepository,
                           PasskeyRepository passkeyRepository,
                           TokenHasher tokenHasher,
                           PasswordEncoder passwordEncoder,
                           EmailSender emailSender,
                           RelyingParty relyingParty,
                           ChallengeStore<PublicKeyCredentialCreationOptions> challengeStore,
                           RecoveryCodeService recoveryCodeService,
                           LoginService loginService,
                           AuditService auditService) {
        this.userRepository = userRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.passkeyRepository = passkeyRepository;
        this.tokenHasher = tokenHasher;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.relyingParty = relyingParty;
        this.challengeStore = challengeStore;
        this.recoveryCodeService = recoveryCodeService;
        this.loginService = loginService;
        this.auditService = auditService;
    }

    /**
     * Start the account recovery flow by sending a verification email.
     */
    @Transactional
    public void startRecovery(String email, String ipAddress, String device) {
        String normalizedEmail = normalizeEmail(email);
        Optional<User> maybeUser = userRepository.findByEmail(normalizedEmail);

        if (maybeUser.isEmpty()) {
            // Enumeration protection: complete without revealing the user does not exist.
            auditService.record(null, "RECOVER_START_UNKNOWN_EMAIL", ipAddress, device);
            return;
        }

        User user = maybeUser.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            // Can only recover ACTIVE accounts.
            auditService.record(user.getId(), "RECOVER_START_INACTIVE_USER", ipAddress, device);
            return;
        }

        // Clean up any unverified tokens to prevent multi-token spam
        emailVerificationRepository.deleteUnverifiedByUserId(user.getId());

        String rawToken = tokenHasher.generateToken();
        String tokenHash = tokenHasher.hash(rawToken);
        Instant expiresAt = Instant.now().plus(EMAIL_TOKEN_TTL);

        emailVerificationRepository.save(new EmailVerification(user.getId(), tokenHash, expiresAt));
        emailSender.sendRecoveryEmail(user.getEmail(), user.getName(), rawToken);

        auditService.record(user.getId(), "RECOVER_START", ipAddress, device);
    }

    /**
     * Verify email token and recovery code. On success, start the registration
     * ceremony for a new passkey.
     */
    @Transactional
    public RecoveryVerifyResponse verifyRecovery(String email, String emailToken, String recoveryCode, String ipAddress, String device) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> AuthException.badRequest("Invalid recovery request."));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw AuthException.badRequest("Invalid recovery request.");
        }

        // 1. Verify email token
        String tokenHash = tokenHasher.hash(emailToken);
        EmailVerification verification = emailVerificationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> AuthException.badRequest("Invalid or expired email verification link."));

        if (verification.isExpired(Instant.now()) || verification.isVerified()) {
            throw AuthException.badRequest("Invalid or expired email verification link.");
        }

        if (!verification.getUserId().equals(user.getId())) {
            throw AuthException.badRequest("Invalid recovery request.");
        }

        // 2. Verify recovery code
        List<RecoveryCode> unusedCodes = recoveryCodeRepository.findByUserId(user.getId()).stream()
                .filter(code -> !code.isUsed())
                .toList();

        RecoveryCode matchedCode = null;
        for (RecoveryCode code : unusedCodes) {
            if (passwordEncoder.matches(recoveryCode, code.getCodeHash())) {
                matchedCode = code;
                break;
            }
        }

        if (matchedCode == null) {
            throw AuthException.badRequest("Invalid recovery code.");
        }

        // Mark the email verification token and the recovery code as used/verified
        verification.markVerified();
        emailVerificationRepository.save(verification);

        matchedCode.markUsed();
        recoveryCodeRepository.save(matchedCode);

        // 3. Initiate registration ceremony for a new passkey
        UserIdentity userIdentity = UserIdentity.builder()
                .name(user.getEmail())
                .displayName(user.getName())
                .id(JpaCredentialRepository.userHandleToByteArray(user.getWebauthnUserHandle()))
                .build();

        AuthenticatorSelectionCriteria selection = AuthenticatorSelectionCriteria.builder()
                .residentKey(ResidentKeyRequirement.PREFERRED)
                .userVerification(UserVerificationRequirement.PREFERRED)
                .build();

        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(
                StartRegistrationOptions.builder()
                        .user(userIdentity)
                        .authenticatorSelection(selection)
                        .build());

        String handle = challengeStore.store(options);

        auditService.record(user.getId(), "RECOVER_VERIFY_SUCCESS", ipAddress, device);

        try {
            return new RecoveryVerifyResponse(handle, options.toCredentialsCreateJson());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize registration options", e);
        }
    }

    /**
     * Complete recovery by verifying the new passkey, deleting all previous
     * passkeys, and issuing a logged-in session.
     */
    @Transactional
    public RecoveryCompleteResult completeRecovery(String handle, String credentialJson, String ipAddress, String device) {
        PublicKeyCredentialCreationOptions options = challengeStore.consume(handle)
                .orElseThrow(() -> AuthException.badRequest("Registration session expired. Please try again."));

        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc;
        try {
            pkc = PublicKeyCredential.parseRegistrationResponseJson(credentialJson);
        } catch (IOException e) {
            throw AuthException.badRequest("Malformed registration response.");
        }

        RegistrationResult result;
        try {
            result = relyingParty.finishRegistration(FinishRegistrationOptions.builder()
                    .request(options)
                    .response(pkc)
                    .build());
        } catch (RegistrationFailedException e) {
            throw AuthException.badRequest("Passkey registration failed verification.");
        }

        // Resolve user
        String email = options.getUser().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> AuthException.badRequest("Account not found."));

        // Revocation Step: Delete ALL existing passkeys for this user
        passkeyRepository.deleteByUserId(user.getId());

        // Save the new passkey
        Passkey passkey = new Passkey(
                user.getId(),
                result.getKeyId().getId().getBase64Url(),
                result.getPublicKeyCose().getBase64Url(),
                result.getSignatureCount(),
                deviceName(device));
        passkey.setLastUsed(Instant.now());
        passkeyRepository.save(passkey);

        // Delete all old recovery codes and generate a new set of 10 recovery codes
        recoveryCodeRepository.deleteByUserId(user.getId());
        List<String> newRecoveryCodes = recoveryCodeService.generateForUser(user.getId());

        // Log the user in (establish a new session)
        LoginResult sessionResult = loginService.createSessionForUser(user, ipAddress, device);

        auditService.record(user.getId(), "RECOVER_COMPLETE", ipAddress, device);

        return new RecoveryCompleteResult(sessionResult.accessToken(), sessionResult.refreshToken(), newRecoveryCodes);
    }

    private static String deviceName(String device) {
        if (device == null || device.isBlank()) {
            return null;
        }
        return device.length() > 255 ? device.substring(0, 255) : device;
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
