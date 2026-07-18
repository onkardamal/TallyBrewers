package com.securebank.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /register.
 */
public record RegisterRequest(
        @NotBlank(message = "Name is required.")
        @Size(max = 255)
        String name,

        @NotBlank(message = "Email is required.")
        @Email(message = "A valid email address is required.")
        @Size(max = 320)
        String email,

        @Size(max = 32, message = "Phone number is too long.")
        String phone
) {
}
