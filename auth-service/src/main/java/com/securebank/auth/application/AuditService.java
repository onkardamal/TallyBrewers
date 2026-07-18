package com.securebank.auth.application;

import com.securebank.auth.domain.AuditLog;
import com.securebank.auth.infrastructure.persistence.AuditLogRepository;

import org.springframework.stereotype.Service;

/**
 * Records security-relevant events to the audit_logs table.
 *
 * Per SECURITY.md, authentication events are audit-logged. This service is a
 * thin, reusable entry point used by the various flow services.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(Long userId, String event, String ipAddress, String device) {
        auditLogRepository.save(new AuditLog(userId, event, ipAddress, device));
    }
}
