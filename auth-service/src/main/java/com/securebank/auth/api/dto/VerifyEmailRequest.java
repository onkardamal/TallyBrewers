package com.securebank.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /verify-email.
 */
public record VerifyEmailRequest(
        @NotBlank(message = "Verification token is required.")
        String token
) {
}
