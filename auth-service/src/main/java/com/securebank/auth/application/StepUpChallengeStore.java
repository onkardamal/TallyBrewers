package com.securebank.auth.application;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for pending step-up (new-device) verification challenges.
 *
 * When a passkey login succeeds from a device the account has never used
 * before, we hold the session issuance behind an emailed one-time code. The
 * pending challenge (which user, the code hash, and the originating request
 * context) lives here between {@code /login/verify} and {@code /login/step-up}.
 *
 * Entries are short-lived, single-use, and attempt-limited. Like the WebAuthn
 * challenge store, this is single-node; swap for a distributed store (e.g.
 * Redis) in a horizontally scaled deployment — no business logic depends on
 * this concrete class.
 */
@Component
public class StepUpChallengeStore {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 5;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();

    public static final class Challenge {
        private final Long userId;
        private final String codeHash;
        private final String ipAddress;
        private final String userAgent;
        private final Instant expiresAt;
        private int attempts;

        Challenge(Long userId, String codeHash, String ipAddress, String userAgent, Instant expiresAt) {
            this.userId = userId;
            this.codeHash = codeHash;
            this.ipAddress = ipAddress;
            this.userAgent = userAgent;
            this.expiresAt = expiresAt;
        }

        public Long userId() {
            return userId;
        }

        public String codeHash() {
            return codeHash;
        }

        public String ipAddress() {
            return ipAddress;
        }

        public String userAgent() {
            return userAgent;
        }
    }

    /** Store a new challenge and return its opaque handle. */
    public String store(Long userId, String codeHash, String ipAddress, String userAgent) {
        purgeExpired();
        byte[] handleBytes = new byte[32];
        secureRandom.nextBytes(handleBytes);
        String handle = urlEncoder.encodeToString(handleBytes);
        challenges.put(handle, new Challenge(userId, codeHash, ipAddress, userAgent,
                Instant.now().plus(TTL)));
        return handle;
    }

    /** Peek at a challenge without consuming it (for attempt-limited verification). */
    public Optional<Challenge> peek(String handle) {
        if (handle == null) {
            return Optional.empty();
        }
        Challenge c = challenges.get(handle);
        if (c == null || Instant.now().isAfter(c.expiresAt)) {
            challenges.remove(handle);
            return Optional.empty();
        }
        return Optional.of(c);
    }

    /** Record a failed attempt; evicts the challenge once the cap is exceeded. */
    public void recordFailedAttempt(String handle) {
        Challenge c = challenges.get(handle);
        if (c != null && ++c.attempts >= MAX_ATTEMPTS) {
            challenges.remove(handle);
        }
    }

    /** Remove a challenge (on successful completion). */
    public void consume(String handle) {
        if (handle != null) {
            challenges.remove(handle);
        }
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        challenges.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt));
    }
}
