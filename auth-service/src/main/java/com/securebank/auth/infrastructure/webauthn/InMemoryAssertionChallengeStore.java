package com.securebank.auth.infrastructure.webauthn;

import com.yubico.webauthn.AssertionRequest;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, single-node implementation of {@link ChallengeStore} for pending
 * WebAuthn assertion (login) ceremonies.
 */
@Component
public class InMemoryAssertionChallengeStore implements ChallengeStore<AssertionRequest> {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public String store(AssertionRequest request) {
        purgeExpired();
        byte[] handleBytes = new byte[32];
        secureRandom.nextBytes(handleBytes);
        String handle = urlEncoder.encodeToString(handleBytes);
        entries.put(handle, new Entry(request, Instant.now().plus(TTL)));
        return handle;
    }

    @Override
    public Optional<AssertionRequest> consume(String handle) {
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

    private record Entry(AssertionRequest request, Instant expiresAt) {
    }
}
