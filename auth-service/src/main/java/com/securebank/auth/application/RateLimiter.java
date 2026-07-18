package com.securebank.auth.application;

/**
 * Minimal rate-limiting abstraction.
 *
 * Implementations decide whether an action identified by a key is currently
 * permitted. Kept behind an interface so the in-memory implementation used
 * now can be swapped for a distributed one (e.g. Redis-backed) later without
 * changing calling code.
 */
public interface RateLimiter {

    /**
     * Attempt to consume one unit of quota for the given key.
     *
     * @return true if the action is allowed, false if the caller is currently
     *         rate-limited and should be rejected.
     */
    boolean tryAcquire(String key);

    /**
     * Clear all rate limiter state. Primarily used to reset thresholds during tests.
     */
    void reset();
}
