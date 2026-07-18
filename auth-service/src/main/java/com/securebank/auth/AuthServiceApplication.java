package com.securebank.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the SecureBank Authentication Service.
 *
 * This service is intentionally scoped to authentication only:
 * registration, email verification, passkey (WebAuthn) registration,
 * passwordless login, session management, and account recovery.
 *
 * See Project Brief.md and SYSTEM_ARCHITECTURE.md at the repository root
 * for the authoritative scope and architecture of this project.
 *
 * UserDetailsServiceAutoConfiguration is excluded: this system is
 * passwordless (WebAuthn only) and has no username/password user store, so
 * Spring Boot's default in-memory user (and its randomly generated startup
 * password) is neither needed nor wanted.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
