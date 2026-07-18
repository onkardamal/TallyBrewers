package com.securebank.auth.application;

import com.securebank.auth.infrastructure.persistence.EmailVerificationRepository;
import com.securebank.auth.infrastructure.persistence.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Scheduled background tasks to clean up expired sessions and verification tokens,
 * preventing long-term database bloat in production.
 */
@Component
public class DatabaseCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(DatabaseCleanupScheduler.class);

    private final SessionRepository sessionRepository;
    private final EmailVerificationRepository emailVerificationRepository;

    public DatabaseCleanupScheduler(SessionRepository sessionRepository,
                                    EmailVerificationRepository emailVerificationRepository) {
        this.sessionRepository = sessionRepository;
        this.emailVerificationRepository = emailVerificationRepository;
    }

    /**
     * Periodically deletes expired database rows (runs every hour).
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredRecords() {
        log.info("Starting background database cleanup...");
        Instant now = Instant.now();

        try {
            int deletedSessions = sessionRepository.deleteExpiredSessions(now);
            if (deletedSessions > 0) {
                log.info("Purged {} expired sessions from the database.", deletedSessions);
            }

            int deletedTokens = emailVerificationRepository.deleteExpiredTokens(now);
            if (deletedTokens > 0) {
                log.info("Purged {} expired verification tokens from the database.", deletedTokens);
            }
        } catch (Exception e) {
            log.error("Failed to complete database cleanup scheduler", e);
        }
    }
}
