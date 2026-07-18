package com.securebank.auth.infrastructure.webauthn;

import java.util.Optional;

/**
 * Server-side storage for pending WebAuthn ceremonies.
 *
 * The WebAuthn challenge (and the associated PublicKeyCredentialCreationOptions
 * request) must be generated and stored on the SERVER between the "start" and
 * "finish" steps of a ceremony — it must never be trusted from the client on
 * finish. This interface abstracts that storage so the in-memory
 * implementation used in development can be swapped for a distributed store
 * (e.g. Redis) in production without touching business logic.
 *
 * Entries are short-lived and single-use: implementations expire them after a
 * timeout and callers remove them on consumption.
 *
 * @param <T> the stored ceremony request type
 */
public interface ChallengeStore<T> {

    /**
     * Store a pending ceremony request under a freshly generated, opaque
     * handle and return that handle to hand back to the client.
     */
    String store(T request);

    /**
     * Atomically retrieve and remove the pending request for the given handle,
     * if present and not expired.
     */
    Optional<T> consume(String handle);
}
