package com.securebank.auth.infrastructure.webauthn;

import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory, single-node implementation of {@link ChallengeStore} for pending
 * WebAuthn registration ceremonies.
 *
 * Suitable for development and single-instance deployments. For a horizontally
 * scaled production deployment, replace this bean with a distributed
 * implementation (e.g. Redis) — no business logic depends on this concrete
 * class, only on the ChallengeStore interface.
 *
 * Entries expire after {@link #TTL} and are single-use (removed on consume).
 */
@Component
public class InMemoryChallengeStore implements ChallengeStore<PublicKeyCredentialCreationOptions> {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public String store(PublicKeyCredentialCreationOptions request) {
        purgeExpired();
        byte[] handleBytes = new byte[32];
        secureRandom.nextBytes(handleBytes);
        String handle = urlEncoder.encodeToString(handleBytes);
        entries.put(handle, new Entry(request, Instant.now().plus(TTL)));
        return handle;
    }

    @Override
    public Optional<PublicKeyCredentialCreationOptions> consume(String handle) {
        if (handle == null) {
            return Optional.empty();
        }
        Entry entry = entries.remove(handle);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(entry.request());
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        entries.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }

    private record Entry(PublicKeyCredentialCreationOptions request, Instant expiresAt) {
    }
}
