package com.securebank.auth.crypto;

import com.securebank.auth.application.TokenHasher;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused cryptographic unit tests, independent of Spring context or database.
 *
 * Verifies the two hashing strategies used in Phase 2:
 *  - Email verification tokens: SHA-256, deterministic, high-entropy tokens.
 *  - Recovery codes: BCrypt, salted/slow, verifiable via matches().
 */
class TokenAndRecoveryCodeCryptoTest {

    private final TokenHasher tokenHasher = new TokenHasher();

    @Test
    void tokenHasher_producesDeterministicSha256Hex() {
        String token = tokenHasher.generateToken();

        String hashA = tokenHasher.hash(token);
        String hashB = tokenHasher.hash(token);

        // Deterministic and 64 hex chars (256 bits).
        assertThat(hashA).isEqualTo(hashB);
        assertThat(hashA).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void tokenHasher_generatesUniqueHighEntropyTokens() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(tokenHasher.generateToken());
        }
        // No collisions across 1000 generations.
        assertThat(tokens).hasSize(1000);
    }

    @Test
    void recoveryCodes_areStoredAsVerifiableBcryptHashes() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        String code = "ABCDE-FGHJK";

        String hash = encoder.encode(code);

        assertThat(hash).startsWith("$2");
        assertThat(encoder.matches(code, hash)).isTrue();
        assertThat(encoder.matches("WRONG-CODE0", hash)).isFalse();
    }
}
