package com.securebank.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A WebAuthn credential (passkey) registered by a user.
 *
 * Stores only the PUBLIC key — the private key never leaves the user's
 * device. credentialId and publicKey are base64url-encoded byte strings as
 * produced by the Yubico WebAuthn library. counter is the signature counter
 * used to detect cloned authenticators.
 */
@Entity
@Table(name = "passkeys")
public class Passkey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "credential_id", nullable = false, unique = true)
    private String credentialId;

    @Column(name = "public_key", nullable = false, columnDefinition = "text")
    private String publicKey;

    @Column(nullable = false)
    private long counter;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "last_used")
    private Instant lastUsed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Passkey() {
        // JPA
    }

    public Passkey(Long userId, String credentialId, String publicKey, long counter, String deviceName) {
        this.userId = userId;
        this.credentialId = credentialId;
        this.publicKey = publicKey;
        this.counter = counter;
        this.deviceName = deviceName;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public long getCounter() {
        return counter;
    }

    public void setCounter(long counter) {
        this.counter = counter;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public Instant getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(Instant lastUsed) {
        this.lastUsed = lastUsed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
