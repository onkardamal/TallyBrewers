package com.securebank.auth.security;

/**
 * Representation of the authenticated principal in the Spring Security context.
 */
public record UserPrincipal(Long id, String email, String name) {
}
