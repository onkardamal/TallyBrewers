package com.securebank.auth.infrastructure.webauthn;

import com.securebank.auth.domain.Passkey;
import com.securebank.auth.domain.User;
import com.securebank.auth.infrastructure.persistence.PasskeyRepository;
import com.securebank.auth.infrastructure.persistence.UserRepository;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.exception.Base64UrlException;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Adapts SecureBank's persistence layer to the Yubico WebAuthn library's
 * {@link CredentialRepository} interface, which the library uses to look up
 * stored credentials during registration and (later) authentication.
 *
 * WebAuthn user handles are stored as UUIDs; on the wire the library uses
 * opaque {@link ByteArray}s. We encode the UUID string as UTF-8 bytes so the
 * mapping is stable and reversible.
 */
@Component
public class JpaCredentialRepository implements CredentialRepository {

    private final UserRepository userRepository;
    private final PasskeyRepository passkeyRepository;

    public JpaCredentialRepository(UserRepository userRepository, PasskeyRepository passkeyRepository) {
        this.userRepository = userRepository;
        this.passkeyRepository = passkeyRepository;
    }

    public static ByteArray userHandleToByteArray(UUID handle) {
        return new ByteArray(handle.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static UUID byteArrayToUserHandle(ByteArray bytes) {
        return UUID.fromString(new String(bytes.getBytes(), StandardCharsets.UTF_8));
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        // "username" here is the user's email.
        return userRepository.findByEmail(username)
                .map(user -> passkeyRepository.findByUserId(user.getId()).stream()
                        .map(this::toDescriptor)
                        .collect(Collectors.toSet()))
                .orElseGet(HashSet::new);
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return userRepository.findByEmail(username)
                .map(user -> userHandleToByteArray(user.getWebauthnUserHandle()));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return userRepository.findByWebauthnUserHandle(byteArrayToUserHandle(userHandle))
                .map(User::getEmail);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return passkeyRepository.findByCredentialId(credentialId.getBase64Url())
                .map(passkey -> toRegisteredCredential(passkey, userHandle));
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return passkeyRepository.findByCredentialId(credentialId.getBase64Url())
                .map(passkey -> {
                    UUID handle = userRepository.findById(passkey.getUserId())
                            .map(User::getWebauthnUserHandle)
                            .orElse(null);
                    if (handle == null) {
                        return Set.<RegisteredCredential>of();
                    }
                    return Set.of(toRegisteredCredential(passkey, userHandleToByteArray(handle)));
                })
                .orElseGet(Set::of);
    }

    private PublicKeyCredentialDescriptor toDescriptor(Passkey passkey) {
        return PublicKeyCredentialDescriptor.builder()
                .id(decodeBase64Url(passkey.getCredentialId()))
                .build();
    }

    private RegisteredCredential toRegisteredCredential(Passkey passkey, ByteArray userHandle) {
        return RegisteredCredential.builder()
                .credentialId(decodeBase64Url(passkey.getCredentialId()))
                .userHandle(userHandle)
                .publicKeyCose(decodeBase64Url(passkey.getPublicKey()))
                .signatureCount(passkey.getCounter())
                .build();
    }

    /**
     * Decode a base64url value that was produced by the WebAuthn library's own
     * {@code getBase64Url()} and stored by us. It is therefore always valid;
     * a decoding failure indicates data corruption, so we surface it as an
     * unchecked exception rather than a checked one.
     */
    private static ByteArray decodeBase64Url(String value) {
        try {
            return ByteArray.fromBase64Url(value);
        } catch (Base64UrlException e) {
            throw new IllegalStateException("Corrupt stored credential data", e);
        }
    }

    // Convenience for callers that already hold the user's passkeys.
    public List<Passkey> passkeysForUser(Long userId) {
        return passkeyRepository.findByUserId(userId);
    }
}
