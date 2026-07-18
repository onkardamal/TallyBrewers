package com.securebank.auth.api;

import com.securebank.auth.api.dto.PasskeyRegisterFinishRequest;
import com.securebank.auth.api.dto.PasskeyRegisterFinishResponse;
import com.securebank.auth.api.dto.PasskeyRegisterStartRequest;
import com.securebank.auth.api.dto.PasskeyRegisterStartResponse;
import com.securebank.auth.application.AuthException;
import com.securebank.auth.application.PasskeyRegistrationService;
import com.securebank.auth.application.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for WebAuthn passkey registration.
 *
 *   POST /passkey/register/start   begin ceremony, return creation options
 *   POST /passkey/register         finish ceremony, verify attestation,
 *                                  store passkey, return one-time recovery
 *                                  codes on first passkey
 *
 * All verification is performed by the Yubico WebAuthn library against a real
 * authenticator response. There is no mock/bypass path.
 */
@RestController
public class PasskeyRegistrationController {

    private final PasskeyRegistrationService passkeyRegistrationService;
    private final RateLimiter rateLimiter;

    public PasskeyRegistrationController(PasskeyRegistrationService passkeyRegistrationService,
                                         RateLimiter rateLimiter) {
        this.passkeyRegistrationService = passkeyRegistrationService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/passkey/register/start")
    public ResponseEntity<PasskeyRegisterStartResponse> start(
            @Valid @RequestBody PasskeyRegisterStartRequest request,
            HttpServletRequest http) {
        String ip = RequestContext.clientIp(http);
        if (!rateLimiter.tryAcquire("passkey-register-start:" + ip)) {
            throw AuthException.tooManyRequests("Too many requests. Please try again later.");
        }

        PasskeyRegistrationService.StartResponse start =
                passkeyRegistrationService.start(request.email());
        return ResponseEntity.ok(
                new PasskeyRegisterStartResponse(start.handle(), start.creationOptionsJson()));
    }

    @PostMapping("/passkey/register")
    public ResponseEntity<PasskeyRegisterFinishResponse> finish(
            @Valid @RequestBody PasskeyRegisterFinishRequest request,
            HttpServletRequest http) {
        String ip = RequestContext.clientIp(http);
        if (!rateLimiter.tryAcquire("passkey-register:" + ip)) {
            throw AuthException.tooManyRequests("Too many requests. Please try again later.");
        }

        PasskeyRegistrationService.FinishResult result = passkeyRegistrationService.finish(
                request.handle(),
                request.credential().toString(),
                ip,
                RequestContext.device(http));
        return ResponseEntity.ok(new PasskeyRegisterFinishResponse(result.recoveryCodes()));
    }
}
