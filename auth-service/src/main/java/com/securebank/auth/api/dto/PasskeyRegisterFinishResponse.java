package com.securebank.auth.api.dto;

import java.util.List;

/**
 * Response body for POST /passkey/register.
 *
 * recoveryCodes contains the one-time plaintext recovery codes, present ONLY
 * on the user's first passkey registration. On subsequent registrations the
 * list is empty. These codes are never retrievable again.
 */
public record PasskeyRegisterFinishResponse(
        List<String> recoveryCodes
) {
}
