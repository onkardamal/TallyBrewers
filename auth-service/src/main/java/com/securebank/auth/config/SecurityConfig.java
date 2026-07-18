package com.securebank.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Base Spring Security configuration for the SecureBank authentication service.
 *
 * This project is passwordless: there is no username/password login form and
 * no traditional Spring Security {@code UserDetailsService}. Authentication
 * is performed via WebAuthn passkeys and results in the issuance of a JWT
 * access token plus a rotating refresh token stored in an HttpOnly cookie.
 *
 * Responsibilities so far:
 * - Disable Spring Security's default form login / HTTP Basic (not used).
 * - Force stateless sessions (no server-side session state; the JWT and the
 *   refresh-token cookie together are the source of truth for authentication).
 * - Enable CSRF protection for cookie-based requests.
 * - Configure a restrictive CORS policy scoped to the frontend origin.
 *
 * Phase 2 endpoints (registration, email verification, passkey registration)
 * are public bootstrap endpoints reached before any authenticated session
 * exists. They are permitted without authentication and exempted from CSRF:
 * CSRF attacks rely on ambient credentials (session cookies), and there are
 * none of those yet. Once login introduces refresh-token cookies (Phase 6),
 * authenticated endpoints will enforce CSRF.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Public, unauthenticated bootstrap endpoints. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/register",
            "/verify-email",
            "/verify-email/resend",
            "/passkey/register/start",
            "/passkey/register",
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(
                                org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers(PUBLIC_ENDPOINTS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // Allow the error dispatch so failures surface with their
                        // real status (e.g. 400 for malformed JSON) instead of
                        // being masked as 403 by the authenticated() fallback.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable());

        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Frontend origin during development; overridden via configuration in production.
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * General-purpose cryptographic hasher, reused for hashing recovery codes
     * and email verification tokens before they are persisted. SecureBank
     * never stores user login passwords, so this bean is not part of a
     * username/password authentication flow.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
