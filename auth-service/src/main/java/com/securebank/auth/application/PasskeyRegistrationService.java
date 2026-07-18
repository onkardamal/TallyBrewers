package com.securebank.auth.application;

import com.securebank.auth.domain.Passkey;
import com.securebank.auth.domain.User;
import com.securebank.auth.domain.UserStatus;
import com.securebank.auth.infrastructure.persistence.PasskeyRepository;
import com.securebank.auth.infrastructure.persistence.UserRepository;
import com.securebank.auth.infrastructure.webauthn.ChallengeStore;
import com.securebank.auth.infrastructure.webauthn.JpaCredentialRepository;
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

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates WebAuthn passkey registration (the "start" and "finish"
 * ceremony steps) using the Yubico java-webauthn-server library.
 *
 * Only VERIFIED users may register a passkey. All cryptographic verification
 * is performed by the Yubico library against a REAL authenticator response —
 * there is no mock/bypass path. On the user's first successful passkey
 * registration, recovery codes are generated and the account becomes ACTIVE;
 * the plaintext recovery codes are returned exactly once at that point.
 */
@Service
public class PasskeyRegistrationService {

    /**
     * Result of finishing registration. recoveryCodes is populated only on the
     * user's first passkey (one-time display); otherwise it is empty.
     */
    public record FinishResult(List<String> recoveryCodes) {
    }

    private final RelyingParty relyingParty;
    private final ChallengeStore<PublicKeyCredentialCreationOptions> challengeStore;
    private final UserRepository userRepository;
    private final PasskeyRepository passkeyRepository;
    private final RecoveryCodeService recoveryCodeService;
    private final AuditService auditService;

    public PasskeyRegistrationService(RelyingParty relyingParty,
                                      ChallengeStore<PublicKeyCredentialCreationOptions> challengeStore,
                                      UserRepository userRepository,
                                      PasskeyRepository passkeyRepository,
                                      RecoveryCodeService recoveryCodeService,
                                      AuditService auditService) {
        this.relyingParty = relyingParty;
        this.challengeStore = challengeStore;
        this.userRepository = userRepository;
        this.passkeyRepository = passkeyRepository;
        this.recoveryCodeService = recoveryCodeService;
        this.auditService = auditService;
    }

    /**
     * Begin a passkey registration ceremony for a verified user.
     *
     * @return a handle identifying the stored ceremony, plus the
     *         PublicKeyCredentialCreationOptions JSON to pass to the browser's
     *         navigator.credentials.create().
     */
    @Transactional(readOnly = true)
    public StartResponse start(String email) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> AuthException.notFound("Account not found."));

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw AuthException.badRequest("Email must be verified before registering a passkey.");
        }

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

        try {
            return new StartResponse(handle, options.toCredentialsCreateJson());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize registration options", e);
        }
    }

    /**
     * Complete a passkey registration ceremony by verifying the authenticator's
     * attestation response against the stored challenge.
     *
     * @param handle              the ceremony handle returned by {@link #start}
     * @param credentialJson      the browser's PublicKeyCredential response JSON
     */
    @Transactional
    public FinishResult finish(String handle, String credentialJson, String ipAddress, String device) {
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

        // Resolve the user from the opaque user handle in the ceremony options.
        String email = options.getUser().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> AuthException.badRequest("Account not found."));

        boolean firstPasskey = !passkeyRepository.existsByUserId(user.getId());

        Passkey passkey = new Passkey(
                user.getId(),
                result.getKeyId().getId().getBase64Url(),
                result.getPublicKeyCose().getBase64Url(),
                result.getSignatureCount(),
                deviceName(device));
        passkey.setLastUsed(Instant.now());
        passkeyRepository.save(passkey);

        List<String> recoveryCodes = List.of();
        if (firstPasskey) {
            recoveryCodes = recoveryCodeService.generateForUser(user.getId());
            if (user.getStatus() != UserStatus.ACTIVE) {
                user.setStatus(UserStatus.ACTIVE);
                userRepository.save(user);
            }
        }

        auditService.record(user.getId(), "PASSKEY_REGISTERED", ipAddress, device);
        return new FinishResult(recoveryCodes);
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

    /** Response for the start step: ceremony handle + browser options JSON. */
    public record StartResponse(String handle, String creationOptionsJson) {
    }

    // Kept for symmetry / potential direct use by callers.
    public Optional<User> findUser(String email) {
        return userRepository.findByEmail(normalizeEmail(email));
    }
}
