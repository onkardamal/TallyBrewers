package com.securebank.auth.api;

import com.securebank.auth.api.dto.MessageResponse;
import com.securebank.auth.api.dto.RegisterRequest;
import com.securebank.auth.api.dto.ResendVerificationRequest;
import com.securebank.auth.api.dto.VerifyEmailRequest;
import com.securebank.auth.application.AuthException;
import com.securebank.auth.application.EmailVerificationService;
import com.securebank.auth.application.RateLimiter;
import com.securebank.auth.application.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for registration and email verification.
 *
 *   POST /register              create account + send verification email
 *   POST /verify-email          verify an email token (single-use)
 *   POST /verify-email/resend   resend verification email (rate-limited)
 */
@RestController
public class RegistrationController {

    private final RegistrationService registrationService;
    private final EmailVerificationService emailVerificationService;
    private final RateLimiter rateLimiter;

    public RegistrationController(RegistrationService registrationService,
                                  EmailVerificationService emailVerificationService,
                                  RateLimiter rateLimiter) {
        this.registrationService = registrationService;
        this.emailVerificationService = emailVerificationService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request,
                                                    HttpServletRequest http) {
        String ip = RequestContext.clientIp(http);
        if (!rateLimiter.tryAcquire("register:" + ip)) {
            throw AuthException.tooManyRequests("Too many requests. Please try again later.");
        }

        registrationService.register(
                request.name(), request.email(), request.phone(),
                ip, RequestContext.device(http));
        // Generic response — never reveals whether the email already existed.
        return ResponseEntity.status(HttpStatus.CREATED).body(new MessageResponse(
                "If the details are valid, a verification email has been sent."));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request,
                                                       HttpServletRequest http) {
        String ip = RequestContext.clientIp(http);
        if (!rateLimiter.tryAcquire("verify-email:" + ip)) {
            throw AuthException.tooManyRequests("Too many requests. Please try again later.");
        }

        emailVerificationService.verify(
                request.token(), ip, RequestContext.device(http));
        return ResponseEntity.ok(new MessageResponse("Email verified. You can now set up your passkey."));
    }

    @PostMapping("/verify-email/resend")
    public ResponseEntity<MessageResponse> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request,
            HttpServletRequest http) {
        registrationService.resendVerification(
                request.email(), RequestContext.clientIp(http), RequestContext.device(http));
        return ResponseEntity.ok(new MessageResponse(
                "If the account exists and is unverified, a new verification email has been sent."));
    }
}
