package com.securebank.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * An immutable record of a security-relevant event.
 *
 * userId is nullable because some events (e.g. a verification attempt with an
 * unknown token) are not attributable to a known user.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String event;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column
    private String device;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    protected AuditLog() {
        // JPA
    }

    public AuditLog(Long userId, String event, String ipAddress, String device) {
        this.userId = userId;
        this.event = event;
        this.ipAddress = ipAddress;
        this.device = device;
        this.timestamp = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEvent() {
        return event;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getDevice() {
        return device;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
