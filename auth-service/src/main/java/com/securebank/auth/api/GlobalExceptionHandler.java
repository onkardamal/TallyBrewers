package com.securebank.auth.api;

import com.securebank.auth.api.dto.MessageResponse;
import com.securebank.auth.application.AuthException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mail.MailException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates application and validation exceptions into safe HTTP responses.
 *
 * Client-facing messages are intentionally generic and never leak internal
 * detail (stack traces, whether an account exists, etc.).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<MessageResponse> handleAuthException(AuthException ex) {
        log.info("AuthException caught: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(new MessageResponse(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MessageResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request.");
        return ResponseEntity.badRequest().body(new MessageResponse(message));
    }

    /**
     * Malformed or unparseable request body → 400 with a generic message,
     * rather than being forwarded to /error (which could otherwise surface as
     * a misleading 403).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<MessageResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new MessageResponse("Malformed request body."));
    }

    /**
     * Outbound email failure (e.g. SMTP unavailable/misconfigured) → 502 with a
     * generic message, rather than a raw 500 stack trace. The underlying cause
     * is logged server-side for operators.
     */
    @ExceptionHandler(MailException.class)
    public ResponseEntity<MessageResponse> handleMailFailure(MailException ex) {
        log.error("Failed to send email", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new MessageResponse(
                "We couldn't send the verification email right now. Please try again later."));
    }
}
