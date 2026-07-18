package com.securebank.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /verify-email/resend.
 */
public record ResendVerificationRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "A valid email address is required.")
        String email
) {
}
