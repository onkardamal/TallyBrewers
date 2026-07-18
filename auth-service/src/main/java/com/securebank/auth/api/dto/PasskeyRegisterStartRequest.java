package com.securebank.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /passkey/register/start.
 *
 * Keyed by email in this phase because login (and thus authenticated sessions)
 * does not exist yet. Once sessions are introduced, this will be derived from
 * the authenticated principal instead.
 */
public record PasskeyRegisterStartRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "A valid email address is required.")
        String email
) {
}
