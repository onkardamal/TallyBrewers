package com.securebank.auth.infrastructure.persistence;

import com.securebank.auth.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
