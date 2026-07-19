package com.securebank.auth.api;

import com.securebank.auth.api.dto.MessageResponse;
import com.securebank.auth.application.AuthException;
import com.securebank.auth.application.LoginService;
import com.securebank.auth.application.LoginService.LoginResult;
import com.securebank.auth.application.QrLoginStore;
import com.securebank.auth.application.TokenHasher;
import com.securebank.auth.domain.User;
import com.securebank.auth.infrastructure.persistence.UserRepository;
import com.securebank.auth.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cross-device ("scan to sign in") login, in the style of WhatsApp Web.
 *
 * Flow:
 *   1. Desktop  POST /login/qr/start   -> {linkId, desktopToken}
 *      Desktop renders a QR encoding an approve URL for linkId, and polls.
 *   2. Phone    POST /login/qr/approve -> approves as the signed-in user
 *      (requires a valid passkey session — this is the security anchor).
 *   3. Desktop  GET  /login/qr/status  -> once APPROVED, the session is issued
 *      to the desktop exactly once (access token + refresh cookie).
 */
@RestController
public class QrLoginController {

    public record StartResponse(String linkId, String desktopToken, int expiresInSeconds) {
    }

    public record ApproveRequest(
            @NotBlank String linkId,
            @NotBlank String handle,
            @jakarta.validation.constraints.NotNull com.fasterxml.jackson.databind.JsonNode credential) {
    }

    public record StatusResponse(String status, String accessToken, LoginController.UserDto user) {
    }

    private final QrLoginStore qrLoginStore;
    private final TokenHasher tokenHasher;
    private final LoginService loginService;
    private final UserRepository userRepository;
    private final CookieHelper cookieHelper;

    public QrLoginController(QrLoginStore qrLoginStore,
                             TokenHasher tokenHasher,
                             LoginService loginService,
                             UserRepository userRepository,
                             CookieHelper cookieHelper) {
        this.qrLoginStore = qrLoginStore;
        this.tokenHasher = tokenHasher;
        this.loginService = loginService;
        this.userRepository = userRepository;
        this.cookieHelper = cookieHelper;
    }

    @PostMapping("/login/qr/start")
    public ResponseEntity<StartResponse> start() {
        String desktopToken = qrLoginStore.randomToken();
        String linkId = qrLoginStore.create(tokenHasher.hash(desktopToken));
        return ResponseEntity.ok(new StartResponse(linkId, desktopToken, 120));
    }

    /**
     * Begin the passkey re-authentication required to approve a sign-in. Returns
     * a WebAuthn assertion challenge for the signed-in user; the phone signs it
     * with a live biometric before {@code /login/qr/approve} is accepted.
     */
    @PostMapping("/login/qr/approve/start")
    public ResponseEntity<LoginService.StartResponse> approveStart(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw AuthException.unauthorized("You must be signed in to approve a sign-in.");
        }
        return ResponseEntity.ok(loginService.start(principal.email()));
    }

    @PostMapping("/login/qr/approve")
    public ResponseEntity<MessageResponse> approve(@Valid @RequestBody ApproveRequest request,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw AuthException.unauthorized("You must be signed in to approve a sign-in.");
        }

        // Require a fresh passkey/biometric tap: the approver must sign the
        // challenge with a passkey they own. An unlocked-but-borrowed phone
        // still can't approve without the owner's Face ID / fingerprint.
        boolean passkeyOk = loginService.verifyAssertionForUser(
                request.handle(), request.credential().toString(), principal.id());
        if (!passkeyOk) {
            throw AuthException.badRequest("Passkey verification failed. Please try again.");
        }

        boolean ok = qrLoginStore.approve(request.linkId(), principal.id());
        if (!ok) {
            throw AuthException.badRequest("This sign-in request has expired. Please refresh the QR code.");
        }
        return ResponseEntity.ok(new MessageResponse("Sign-in approved."));
    }

    @GetMapping("/login/qr/status")
    public ResponseEntity<StatusResponse> status(@RequestParam("linkId") String linkId,
                                                 @RequestParam("desktopToken") String desktopToken,
                                                 HttpServletRequest http,
                                                 HttpServletResponse response) {
        QrLoginStore.Link link = qrLoginStore.peek(linkId).orElse(null);
        if (link == null) {
            return ResponseEntity.ok(new StatusResponse("EXPIRED", null, null));
        }

        // Only the desktop that started this link (holds the secret token) may
        // read its status or collect the session.
        if (desktopToken == null
                || !tokenHasher.hash(desktopToken).equals(link.desktopTokenHash())) {
            throw AuthException.unauthorized("Invalid device token.");
        }

        if (link.status() == QrLoginStore.Status.PENDING) {
            return ResponseEntity.ok(new StatusResponse("PENDING", null, null));
        }

        if (link.status() == QrLoginStore.Status.APPROVED && link.userId() != null) {
            User user = userRepository.findById(link.userId())
                    .orElseThrow(() -> AuthException.unauthorized("User not found."));

            String ip = RequestContext.clientIp(http);
            LoginResult result = loginService.createSessionForUser(user, ip, RequestContext.device(http));
            cookieHelper.setRefreshTokenCookie(response, result.refreshToken());

            // Session issued exactly once; the link cannot be replayed.
            qrLoginStore.consume(linkId);

            LoginController.UserDto userDto = new LoginController.UserDto(
                    user.getId(), user.getName(), user.getEmail(), user.getPhone());
            return ResponseEntity.ok(new StatusResponse("APPROVED", result.accessToken(), userDto));
        }

        return ResponseEntity.ok(new StatusResponse("EXPIRED", null, null));
    }
}
