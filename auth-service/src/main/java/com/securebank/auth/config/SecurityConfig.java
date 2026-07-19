package com.securebank.auth.config;

import com.securebank.auth.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Public, unauthenticated bootstrap endpoints that do not use cookies. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/register",
            "/verify-email",
            "/verify-email/resend",
            "/passkey/register/start",
            "/passkey/register",
            "/login/start",
            "/login/verify",
            "/login/step-up",
            "/login/qr/start",
            "/login/qr/status",
            "/recover/start",
            "/recover/verify",
            "/recover/passkey",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler requestHandler =
                new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler();
        // By setting requestAttributeName to null, we defer loading of the CSRF token,
        // but still use the non-XOR handler.
        requestHandler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(
                                org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(requestHandler)
                        .ignoringRequestMatchers(PUBLIC_ENDPOINTS))
                // Network-security hardening: send defensive HTTP response headers
                // on every API response.
                .headers(headers -> headers
                        // Clickjacking: this API is never meant to be framed.
                        .frameOptions(frame -> frame.deny())
                        // MIME-sniffing protection.
                        .contentTypeOptions(withDefaults -> {})
                        // Force HTTPS for a year once served over TLS (ignored on plain-http localhost).
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        // Don't leak full URLs (which may carry tokens) in the Referer header.
                        .referrerPolicy(ref -> ref.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // Lock down what the API's own responses may do/load.
                        // Permits the bundled Swagger UI while blocking framing,
                        // object embeds, and off-origin resource loads.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; base-uri 'self'; "
                                        + "object-src 'none'; script-src 'self' 'unsafe-inline'; "
                                        + "style-src 'self' 'unsafe-inline'; img-src 'self' data:"))
                        .addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter(
                                "Permissions-Policy", "geolocation=(), camera=(), microphone=(), payment=()")))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // /session/refresh and /logout are cookie-based public endpoints but need to be accessible
                        .requestMatchers("/session/refresh", "/logout").permitAll()
                        // Allow the error dispatch so failures surface with their real status
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .logout(logout -> logout.disable())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new org.springframework.web.filter.OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                                    jakarta.servlet.http.HttpServletResponse response,
                                                    jakarta.servlet.FilterChain filterChain)
                            throws jakarta.servlet.ServletException, java.io.IOException {
                        org.springframework.security.web.csrf.CsrfToken csrfToken =
                                (org.springframework.security.web.csrf.CsrfToken) request.getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
                        if (csrfToken != null) {
                            csrfToken.getToken();
                        }
                        filterChain.doFilter(request, response);
                    }
                }, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Frontend origin during development; overridden via configuration in production.
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        configuration.setExposedHeaders(List.of("Set-Cookie"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * General-purpose cryptographic hasher, reused for hashing recovery codes
     * and email verification tokens before they are persisted.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
