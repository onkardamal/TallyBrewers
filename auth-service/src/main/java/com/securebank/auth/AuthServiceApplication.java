package com.securebank.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import org.springframework.scheduling.annotation.EnableScheduling;

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
@EnableScheduling
public class AuthServiceApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(AuthServiceApplication.class, args);
    }

    private static void loadDotEnv() {
        java.io.File envFile = new java.io.File(".env");
        if (!envFile.exists()) {
            envFile = new java.io.File("auth-service/.env");
        }
        if (!envFile.exists()) {
            envFile = new java.io.File("../.env");
        }
        if (envFile.exists()) {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(envFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int equalsIdx = line.indexOf('=');
                    if (equalsIdx > 0) {
                        String key = line.substring(0, equalsIdx).trim();
                        String val = line.substring(equalsIdx + 1).trim();
                        if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                            val = val.substring(1, val.length() - 1);
                        } else if (val.startsWith("'") && val.endsWith("'") && val.length() >= 2) {
                            val = val.substring(1, val.length() - 1);
                        }
                        if (System.getenv(key) == null && System.getProperty(key) == null) {
                            System.setProperty(key, val);
                        }
                    }
                }
            } catch (java.io.IOException e) {
                // Ignore or log
            }
        }
    }
}
