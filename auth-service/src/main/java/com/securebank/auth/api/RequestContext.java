package com.securebank.auth.api;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Small helper for extracting audit context (client IP, device/user-agent)
 * from the incoming HTTP request.
 */
final class RequestContext {

    private RequestContext() {
    }

    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // First entry is the original client.
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    static String device(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}
