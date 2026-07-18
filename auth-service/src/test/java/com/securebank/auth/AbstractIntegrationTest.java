package com.securebank.auth;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests.
 *
 * Tests run against a REAL local PostgreSQL 17 instance managed by Testcontainers
 * in a Docker daemon, so Flyway migrations and JPA mappings are exercised against
 * the same database engine used in production — not an in-memory H2 substitute.
 * SMTP is pointed at a local in-process GreenMail server (see individual test classes).
 *
 * Each test starts from a clean data state: all tables are truncated (schema
 * is preserved — it is owned by Flyway migrations) before every test method.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("securebank_test")
            .withUsername("securebank_app")
            .withPassword("securebank_password");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.securebank.auth.application.RateLimiter rateLimiter;

    @BeforeEach
    void resetDatabase() {
        rateLimiter.reset();
        // Order-independent thanks to CASCADE; RESTART IDENTITY resets sequences.
        jdbcTemplate.execute(
                "TRUNCATE TABLE audit_logs, recovery_codes, email_verifications, "
                        + "passkeys, sessions, users RESTART IDENTITY CASCADE");
    }
}
