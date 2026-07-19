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
 * In-memory store backing cross-device ("scan to sign in") logins.
 *
 * Security model — the desktop can never sign itself in:
 *  - {@code /login/qr/start} mints a link in PENDING state and hands the
 *    desktop a secret {@code desktopToken} (stored only as a hash). Only a
 *    caller holding that token may collect the resulting session.
 *  - An ALREADY-AUTHENTICATED device (the phone) moves the link to APPROVED,
 *    binding it to that user. Approval therefore requires a real passkey
 *    session — scanning alone does nothing.
 *  - Links are short-lived and single-use; the session is issued exactly once
 *    when the desktop polls after approval, then the link is CONSUMED.
 *
 * Single-node like the other in-memory stores; swap for Redis in a scaled
 * deployment without touching business logic.
 */
@Component
public class QrLoginStore {

    public enum Status { PENDING, APPROVED, CONSUMED }

    private static final Duration TTL = Duration.ofMinutes(2);

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();
    private final Map<String, Link> links = new ConcurrentHashMap<>();

    public static final class Link {
        private final String desktopTokenHash;
        private final Instant expiresAt;
        private volatile Status status = Status.PENDING;
        private volatile Long userId;

        Link(String desktopTokenHash, Instant expiresAt) {
            this.desktopTokenHash = desktopTokenHash;
            this.expiresAt = expiresAt;
        }

        public Status status() {
            return status;
        }

        public Long userId() {
            return userId;
        }

        public String desktopTokenHash() {
            return desktopTokenHash;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /** Create a PENDING link; returns the generated linkId. */
    public String create(String desktopTokenHash) {
        purgeExpired();
        String linkId = randomToken();
        links.put(linkId, new Link(desktopTokenHash, Instant.now().plus(TTL)));
        return linkId;
    }

    public Optional<Link> peek(String linkId) {
        if (linkId == null) {
            return Optional.empty();
        }
        Link link = links.get(linkId);
        if (link == null || link.isExpired()) {
            links.remove(linkId);
            return Optional.empty();
        }
        return Optional.of(link);
    }

    /** Move a PENDING link to APPROVED, bound to the approving user. */
    public boolean approve(String linkId, Long userId) {
        Link link = peek(linkId).orElse(null);
        if (link == null || link.status != Status.PENDING) {
            return false;
        }
        link.userId = userId;
        link.status = Status.APPROVED;
        return true;
    }

    /** Mark an APPROVED link CONSUMED after the session has been issued once. */
    public void consume(String linkId) {
        Link link = links.get(linkId);
        if (link != null) {
            link.status = Status.CONSUMED;
            links.remove(linkId);
        }
    }

    public String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return urlEncoder.encodeToString(bytes);
    }

    private void purgeExpired() {
        links.entrySet().removeIf(e -> e.getValue().isExpired());
    }
}
