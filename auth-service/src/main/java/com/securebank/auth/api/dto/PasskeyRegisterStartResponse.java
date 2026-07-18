package com.securebank.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;

/**
 * Response body for POST /passkey/register/start.
 *
 * handle identifies the pending server-side ceremony and must be sent back on
 * finish. creationOptions is the raw PublicKeyCredentialCreationOptions JSON
 * produced by the WebAuthn library, passed directly to the browser's
 * navigator.credentials.create().
 */
public record PasskeyRegisterStartResponse(
        String handle,
        @JsonRawValue String creationOptions
) {
}
