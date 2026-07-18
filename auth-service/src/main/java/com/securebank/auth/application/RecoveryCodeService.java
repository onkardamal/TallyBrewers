package com.securebank.auth.application;

import com.securebank.auth.domain.RecoveryCode;
import com.securebank.auth.infrastructure.persistence.RecoveryCodeRepository;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates and persists single-use account recovery codes.
 *
 * Codes are cryptographically random and formatted for human transcription
 * (groups of characters from an unambiguous alphabet). Only the BCrypt hash
 * of each code is stored; the plaintext codes are returned to the caller
 * exactly once (to be shown to the user immediately after passkey
 * registration) and can never be retrieved again.
 */
@Service
public class RecoveryCodeService {

    /** Number of recovery codes generated per user. */
    static final int CODE_COUNT = 10;
    /** Number of characters in each of the two groups of a code. */
    private static final int GROUP_LENGTH = 5;
    /** Unambiguous alphabet (no 0/O, 1/I/L) for human transcription. */
    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private final RecoveryCodeRepository recoveryCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public RecoveryCodeService(RecoveryCodeRepository recoveryCodeRepository,
                               PasswordEncoder passwordEncoder) {
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Generate a fresh set of recovery codes for the user, persist their
     * BCrypt hashes, and return the plaintext codes for one-time display.
     */
    @Transactional
    public List<String> generateForUser(Long userId) {
        List<String> plaintextCodes = new ArrayList<>(CODE_COUNT);
        List<RecoveryCode> toPersist = new ArrayList<>(CODE_COUNT);

        for (int i = 0; i < CODE_COUNT; i++) {
            String code = generateCode();
            plaintextCodes.add(code);
            toPersist.add(new RecoveryCode(userId, passwordEncoder.encode(code)));
        }

        recoveryCodeRepository.saveAll(toPersist);
        return plaintextCodes;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(GROUP_LENGTH * 2 + 1);
        for (int i = 0; i < GROUP_LENGTH; i++) {
            sb.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        sb.append('-');
        for (int i = 0; i < GROUP_LENGTH; i++) {
            sb.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
