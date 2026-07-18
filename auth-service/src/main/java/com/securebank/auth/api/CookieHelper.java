package com.securebank.auth.api;

import com.securebank.auth.config.SecureBankProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Shared helper for standard HTTP-only session cookies.
 */
@Component
public class CookieHelper {

    private final SecureBankProperties properties;

    public CookieHelper(SecureBankProperties properties) {
        this.properties = properties;
    }

    /**
     * Sets the rotating HTTP-only refresh token cookie.
     */
    public void setRefreshTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(properties.getJwt().getRefreshTokenTtlDays() * 24L * 60 * 60)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Clears the HTTP-only refresh token cookie.
     */
    public void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
