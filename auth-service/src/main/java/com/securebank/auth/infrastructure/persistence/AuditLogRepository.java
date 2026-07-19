package com.securebank.auth.infrastructure.persistence;

import com.securebank.auth.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Whether this user has ever produced an audit event from the given device
     * (user-agent). Used for new-device detection: a device with no prior
     * history triggers step-up verification at login.
     */
    boolean existsByUserIdAndDevice(Long userId, String device);
}
