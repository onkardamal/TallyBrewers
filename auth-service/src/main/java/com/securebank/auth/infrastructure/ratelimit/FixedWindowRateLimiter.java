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
 * Allows up to {@code maxRequests} actions per key within a fixed window
 * (both configurable via {@code securebank.rate-limit.*}). This throttles
 * authentication endpoints against brute-force and email-flood abuse while
 * still permitting the handful of calls a legitimate user makes in a normal
 * flow. Single-node only; replace with a distributed implementation (e.g.
 * Redis) for horizontally scaled deployments.
 */
@Component
public class FixedWindowRateLimiter implements RateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final SecureBankProperties properties;

    public FixedWindowRateLimiter(SecureBankProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean tryAcquire(String key) {
        SecureBankProperties.RateLimit config = properties.getRateLimit();
        if (!config.isEnabled()) {
            return true;
        }
        Instant now = Instant.now();
        Duration windowLength = Duration.ofSeconds(config.getWindowSeconds());

        // Atomically start a new window or increment the current one.
        Window updated = windows.compute(key, (k, existing) -> {
            if (existing == null || now.isAfter(existing.start.plus(windowLength))) {
                return new Window(now, 1);
            }
            return new Window(existing.start, existing.count + 1);
        });

        return updated.count <= config.getMaxRequests();
    }

    @Override
    public void reset() {
        windows.clear();
    }

    private static final class Window {
        private final Instant start;
        private final int count;

        Window(Instant start, int count) {
            this.start = start;
            this.count = count;
        }
    }
}
