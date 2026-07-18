package com.securebank.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A registered user of the SecureBank authentication system.
 *
 * Maps to the "users" table (see Flyway V1 + V2). No passwords are ever
 * stored — authentication is via WebAuthn passkeys.
 *
 * webauthnUserHandle is a stable, opaque per-user identifier used as the
 * WebAuthn user.id. It is a random UUID rather than the numeric primary key
 * (to avoid enumeration leakage) or the email (which is PII and may change).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Column(name = "webauthn_user_handle", nullable = false, unique = true, updatable = false)
    private UUID webauthnUserHandle;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
        // JPA
    }

    public User(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.status = UserStatus.PENDING_VERIFICATION;
        this.webauthnUserHandle = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public UUID getWebauthnUserHandle() {
        return webauthnUserHandle;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
