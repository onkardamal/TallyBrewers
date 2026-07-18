package com.securebank.auth.api;

import com.securebank.auth.api.dto.PasskeyRegisterFinishRequest;
import com.securebank.auth.api.dto.PasskeyRegisterFinishResponse;
import com.securebank.auth.api.dto.PasskeyRegisterStartRequest;
import com.securebank.auth.api.dto.PasskeyRegisterStartResponse;
import com.securebank.auth.application.PasskeyRegistrationService;
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

    public PasskeyRegistrationController(PasskeyRegistrationService passkeyRegistrationService) {
        this.passkeyRegistrationService = passkeyRegistrationService;
    }

    @PostMapping("/passkey/register/start")
    public ResponseEntity<PasskeyRegisterStartResponse> start(
            @Valid @RequestBody PasskeyRegisterStartRequest request) {
        PasskeyRegistrationService.StartResponse start =
                passkeyRegistrationService.start(request.email());
        return ResponseEntity.ok(
                new PasskeyRegisterStartResponse(start.handle(), start.creationOptionsJson()));
    }

    @PostMapping("/passkey/register")
    public ResponseEntity<PasskeyRegisterFinishResponse> finish(
            @Valid @RequestBody PasskeyRegisterFinishRequest request,
            HttpServletRequest http) {
        PasskeyRegistrationService.FinishResult result = passkeyRegistrationService.finish(
                request.handle(),
                request.credential().toString(),
                RequestContext.clientIp(http),
                RequestContext.device(http));
        return ResponseEntity.ok(new PasskeyRegisterFinishResponse(result.recoveryCodes()));
    }
}
