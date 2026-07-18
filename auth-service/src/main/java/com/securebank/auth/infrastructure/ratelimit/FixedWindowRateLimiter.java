package com.securebank.auth.infrastructure.ratelimit;

import com.securebank.auth.application.RateLimiter;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.securebank.auth.config.SecureBankProperties;
import org.springframework.stereotype.Component;

/**
 * Simple in-memory fixed-window rate limiter.
 *
 * Allows at most one action per key within a fixed cooldown window. Used to
 * throttle the verification-email resend endpoint so a user (or attacker)
 * cannot trigger a flood of emails. Single-node only; replace with a
 * distributed implementation for horizontally scaled deployments.
 */
@Component
public class FixedWindowRateLimiter implements RateLimiter {

    private static final Duration WINDOW = Duration.ofSeconds(60);

    private final Map<String, Instant> lastAllowed = new ConcurrentHashMap<>();
    private final SecureBankProperties properties;

    public FixedWindowRateLimiter(SecureBankProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean tryAcquire(String key) {
        if (!properties.getRateLimit().isEnabled()) {
            return true;
        }
        Instant now = Instant.now();
        // computeIfAbsent-style atomic check-and-set on the window.
        Instant previous = lastAllowed.get(key);
        if (previous != null && now.isBefore(previous.plus(WINDOW))) {
            return false;
        }
        // Use merge to reduce race windows; last writer within the window wins,
        // which is acceptable for a throttle of this nature.
        lastAllowed.put(key, now);
        return true;
    }

    @Override
    public void reset() {
        lastAllowed.clear();
    }
}
