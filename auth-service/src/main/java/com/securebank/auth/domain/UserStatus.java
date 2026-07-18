package com.securebank.auth.domain;

/**
 * Lifecycle status of a user account.
 *
 * PENDING_VERIFICATION - registered but email not yet verified
 * VERIFIED             - email verified; may register a passkey
 * ACTIVE               - has at least one passkey and can authenticate
 */
public enum UserStatus {
    PENDING_VERIFICATION,
    VERIFIED,
    ACTIVE
}
