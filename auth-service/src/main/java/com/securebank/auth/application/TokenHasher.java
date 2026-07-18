package com.securebank.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

/**
 * Generates and hashes high-entropy opaque tokens (used for email
 * verification).
 *
 * Email verification tokens are hashed with SHA-256 before storage. SHA-256
 * (rather than BCrypt) is appropriate here because these tokens are long,
 * random, high-entropy values — not user-chosen secrets — so they are not
 * vulnerable to brute-force/dictionary attacks that BCrypt's slow hashing
 * defends against. Recovery codes, which are shorter and human-transcribable,
 * use BCrypt instead (see RecoveryCodeService).
 */
@Component
public class TokenHasher {

    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();

    /**
     * Generate a new URL-safe random token (the raw value that is emailed to
     * the user and never stored).
     */
    public String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return urlEncoder.encodeToString(bytes);
    }

    /**
     * Compute the SHA-256 hash (hex-encoded) of a token for storage/lookup.
     */
    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            // SHA-256 is guaranteed present on every JVM; this should never happen.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
