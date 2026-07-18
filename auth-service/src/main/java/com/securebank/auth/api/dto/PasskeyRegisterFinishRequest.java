package com.securebank.auth.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /passkey/register.
 *
 * handle is the ceremony handle returned by the start step. credential is the
 * raw PublicKeyCredential attestation response produced by the browser's
 * navigator.credentials.create(); it is passed through to the WebAuthn library
 * verbatim as JSON.
 */
public record PasskeyRegisterFinishRequest(
        @NotBlank(message = "Registration session handle is required.")
        String handle,

        @NotNull(message = "Credential is required.")
        JsonNode credential
) {
}
