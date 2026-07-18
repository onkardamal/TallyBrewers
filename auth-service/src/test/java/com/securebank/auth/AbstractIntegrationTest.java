package com.securebank.auth;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for integration tests.
 *
 * Tests run against a REAL local PostgreSQL 17 instance (the dedicated
 * `securebank_test` database configured in application-test.yml), so Flyway
 * migrations and JPA mappings are exercised against the same database engine
 * used in production — not an in-memory H2 substitute. SMTP is pointed at a
 * local in-process GreenMail server (see individual test classes).
 *
 * Each test starts from a clean data state: all tables are truncated (schema
 * is preserved — it is owned by Flyway migrations) before every test method.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        // Order-independent thanks to CASCADE; RESTART IDENTITY resets sequences.
        jdbcTemplate.execute(
                "TRUNCATE TABLE audit_logs, recovery_codes, email_verifications, "
                        + "passkeys, sessions, users RESTART IDENTITY CASCADE");
    }
}
