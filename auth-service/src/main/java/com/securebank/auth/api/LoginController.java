package com.securebank.auth.api;

import com.securebank.auth.api.dto.MessageResponse;
import com.securebank.auth.application.AuthException;
import com.securebank.auth.application.LoginService;
import com.securebank.auth.application.LoginService.LoginResult;
import com.securebank.auth.application.LoginService.StartResponse;
import com.securebank.auth.application.RateLimiter;
import com.securebank.auth.domain.User;
import com.securebank.auth.infrastructure.persistence.UserRepository;
import com.securebank.auth.security.UserPrincipal;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
public class LoginController {

    public record LoginStartRequest(@NotBlank @Email String email) {
    }

    public record LoginVerifyRequest(@NotBlank String handle, @jakarta.validation.constraints.NotNull com.fasterxml.jackson.databind.JsonNode credential) {
    }

    public record LoginResponse(String accessToken, UserDto user) {
    }

    public record UserDto(Long id, String name, String email, String phone) {
    }

    private final LoginService loginService;
    private final UserRepository userRepository;
    private final RateLimiter rateLimiter;
    private final CookieHelper cookieHelper;

    public LoginController(LoginService loginService,
                           UserRepository userRepository,
                           RateLimiter rateLimiter,
                           CookieHelper cookieHelper) {
        this.loginService = loginService;
        this.userRepository = userRepository;
        this.rateLimiter = rateLimiter;
        this.cookieHelper = cookieHelper;
    }

    @PostMapping("/login/start")
    public ResponseEntity<StartResponse> loginStart(@Valid @RequestBody LoginStartRequest request,
                                                    HttpServletRequest http) {
        String ip = RequestContext.clientIp(http);
        if (!rateLimiter.tryAcquire("login-start:" + ip)) {
            throw AuthException.tooManyRequests("Too many requests. Please try again later.");
        }

        StartResponse startResponse = loginService.start(request.email());
        return ResponseEntity.ok(startResponse);
    }

    @PostMapping("/login/verify")
    public ResponseEntity<LoginResponse> loginVerify(@Valid @RequestBody LoginVerifyRequest request,
                                                     HttpServletRequest http,
                                                     HttpServletResponse response) {
        String ip = RequestContext.clientIp(http);
        if (!rateLimiter.tryAcquire("login-verify:" + ip)) {
            throw AuthException.tooManyRequests("Too many requests. Please try again later.");
        }

        LoginResult result = loginService.verify(
                request.handle(),
                request.credential().toString(),
                ip,
                RequestContext.device(http)
        );

        cookieHelper.setRefreshTokenCookie(response, result.refreshToken());

        UserDto userDto = new UserDto(
                result.user().getId(),
                result.user().getName(),
                result.user().getEmail(),
                result.user().getPhone()
        );
        return ResponseEntity.ok(new LoginResponse(result.accessToken(), userDto));
    }

    @PostMapping("/session/refresh")
    public ResponseEntity<LoginResponse> sessionRefresh(HttpServletRequest http,
                                                        HttpServletResponse response) {
        String ip = RequestContext.clientIp(http);
        if (!rateLimiter.tryAcquire("session-refresh:" + ip)) {
            throw AuthException.tooManyRequests("Too many requests. Please try again later.");
        }

        String refreshToken = extractRefreshToken(http);
        LoginResult result = loginService.refresh(
                refreshToken,
                ip,
                RequestContext.device(http)
        );

        cookieHelper.setRefreshTokenCookie(response, result.refreshToken());

        UserDto userDto = new UserDto(
                result.user().getId(),
                result.user().getName(),
                result.user().getEmail(),
                result.user().getPhone()
        );
        return ResponseEntity.ok(new LoginResponse(result.accessToken(), userDto));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(HttpServletRequest http,
                                                  HttpServletResponse response) {
        String refreshToken = extractRefreshToken(http);
        loginService.logout(refreshToken);
        cookieHelper.clearRefreshTokenCookie(response);
        return ResponseEntity.ok(new MessageResponse("Logged out successfully."));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw AuthException.unauthorized("Not authenticated.");
        }
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> AuthException.unauthorized("User not found."));

        return ResponseEntity.ok(new UserDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone()
        ));
    }

    private String extractRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> "refresh_token".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
