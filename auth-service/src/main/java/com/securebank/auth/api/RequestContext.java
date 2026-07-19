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
        String ip;
        if (forwarded != null && !forwarded.isBlank()) {
            // First entry is the original client.
            ip = forwarded.split(",")[0].trim();
        } else {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.length() > 45) {
            return ip.substring(0, 45);
        }
        return ip;
    }

    static String device(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua != null && ua.length() > 255) {
            return ua.substring(0, 255);
        }
        return ua;
    }
}
