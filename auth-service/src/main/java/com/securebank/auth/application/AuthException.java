package com.securebank.auth.application;

import org.springframework.http.HttpStatus;

/**
 * Application-level exception carrying an HTTP status and a safe, client-facing
 * message. Thrown by services and translated to an HTTP response by the
 * global exception handler. Messages must never leak sensitive detail.
 */
public class AuthException extends RuntimeException {

    private final HttpStatus status;

    public AuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static AuthException badRequest(String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, message);
    }

    public static AuthException conflict(String message) {
        return new AuthException(HttpStatus.CONFLICT, message);
    }

    public static AuthException tooManyRequests(String message) {
        return new AuthException(HttpStatus.TOO_MANY_REQUESTS, message);
    }

    public static AuthException notFound(String message) {
        return new AuthException(HttpStatus.NOT_FOUND, message);
    }
}
