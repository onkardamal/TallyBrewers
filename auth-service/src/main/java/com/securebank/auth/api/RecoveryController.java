package com.securebank.auth.api;

import com.securebank.auth.api.dto.MessageResponse;
import com.securebank.auth.application.AuthException;
import com.securebank.auth.application.RateLimiter;
import com.securebank.auth.application.RecoveryService;
import com.securebank.auth.application.RecoveryService.RecoveryCompleteResult;
import com.securebank.auth.application.RecoveryService.RecoveryVerifyResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecoveryController {

    public record RecoveryStartRequest(@NotBlank @Email String email) {
    }

    public record RecoveryVerifyRequest(
            @NotBlank @Email String email,
            @NotBlank String token,
            @NotBlank String recoveryCode
    ) {
    }

    public record RecoveryCompleteRequest(
            @NotBlank String handle,
            @jakarta.validation.constraints.NotNull com.fasterxml.jackson.databind.JsonNode credential
    ) {
    }

    public record RecoveryCompleteResponse(
            String accessToken,
            java.util.List<String> recoveryCodes
    ) {
    }

    private final RecoveryService recoveryService;
    private final RateLimiter rateLimiter;
    private final CookieHelper cookieHelper;

    public RecoveryController(RecoveryService recoveryService,
                              RateLimiter rateLimiter,
                              CookieHelper cookieHelper) {
        this.recoveryService = recoveryService;
        this.rateLimiter = rateLimiter;
        this.cookieHelper = cookieHelper;
    }

    @PostMapping("/recover/start")
    public ResponseEntity<MessageResponse> startRecovery(@Valid @RequestBody RecoveryStartRequest request,
                                                         HttpServletRequest http) {
        String ip = RequestContext.clientIp(http);
        if (!rateLimiter.tryAcquire("recover-start:" + ip)) {
            throw AuthException.tooManyRequests("Too many requests. Please try again later.");
        }

        recoveryService.startRecovery(request.email(), ip, RequestContext.device(http));
        return ResponseEntity.ok(new MessageResponse(
                "If the account exists and is active, a recovery link has been sent."));
    }

    @PostMapping("/recover/verify")
    public ResponseEntity<RecoveryVerifyResponse> verifyRecovery(@Valid @RequestBody RecoveryVerifyRequest request,
                                                                 HttpServletRequest http) {
        String ip = RequestContext.clientIp(http);
        if (!rateLimiter.tryAcquire("recover-verify:" + ip)) {
            throw AuthException.tooManyRequests("Too many requests. Please try again later.");
        }

        RecoveryVerifyResponse verifyResponse = recoveryService.verifyRecovery(
                request.email(),
                request.token(),
                request.recoveryCode(),
                ip,
                RequestContext.device(http)
        );
        return ResponseEntity.ok(verifyResponse);
    }

    @PostMapping("/recover/passkey")
    public ResponseEntity<RecoveryCompleteResponse> completeRecovery(@Valid @RequestBody RecoveryCompleteRequest request,
                                                                     HttpServletRequest http,
                                                                     HttpServletResponse response) {
        String ip = RequestContext.clientIp(http);
        if (!rateLimiter.tryAcquire("recover-passkey:" + ip)) {
            throw AuthException.tooManyRequests("Too many requests. Please try again later.");
        }

        RecoveryCompleteResult result = recoveryService.completeRecovery(
                request.handle(),
                request.credential().toString(),
                ip,
                RequestContext.device(http)
        );

        cookieHelper.setRefreshTokenCookie(response, result.refreshToken());

        return ResponseEntity.ok(new RecoveryCompleteResponse(result.accessToken(), result.recoveryCodes()));
    }
}
