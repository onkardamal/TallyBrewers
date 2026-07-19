package com.securebank.auth.api;

import com.securebank.auth.api.dto.MessageResponse;
import com.securebank.auth.application.AuthException;
import com.securebank.auth.application.TokenHasher;
import com.securebank.auth.domain.Session;
import com.securebank.auth.infrastructure.persistence.SessionRepository;
import com.securebank.auth.security.UserPrincipal;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Read + revoke access to the authenticated user's active sessions.
 *
 * Every value returned here is real, persisted session data (IP address,
 * user-agent, timestamps) captured at login time — no synthetic or mock
 * records. This powers the "Devices & sessions" security panel and lets a user
 * remotely sign a lost or unrecognised device out.
 */
@RestController
public class SessionController {

    /**
     * A single active session as shown in the security panel. {@code current}
     * marks the session tied to the caller's own refresh-token cookie.
     */
    public record SessionDto(
            Long id,
            String ipAddress,
            String userAgent,
            Instant createdAt,
            Instant lastActivity,
            Instant expiresAt,
            boolean current) {
    }

    private final SessionRepository sessionRepository;
    private final TokenHasher tokenHasher;

    public SessionController(SessionRepository sessionRepository, TokenHasher tokenHasher) {
        this.sessionRepository = sessionRepository;
        this.tokenHasher = tokenHasher;
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionDto>> listSessions(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest http) {
        if (principal == null) {
            throw AuthException.unauthorized("Not authenticated.");
        }

        String currentHash = currentSessionHash(http);
        Instant now = Instant.now();

        List<SessionDto> sessions = sessionRepository.findByUserId(principal.id()).stream()
                .filter(s -> s.getExpiresAt().isAfter(now))
                .sorted(Comparator.comparing(Session::getLastActivity).reversed())
                .map(s -> new SessionDto(
                        s.getId(),
                        s.getIpAddress(),
                        s.getUserAgent(),
                        s.getCreatedAt(),
                        s.getLastActivity(),
                        s.getExpiresAt(),
                        currentHash != null && currentHash.equals(s.getRefreshTokenHash())))
                .toList();

        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<MessageResponse> revokeSession(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        if (principal == null) {
            throw AuthException.unauthorized("Not authenticated.");
        }

        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> AuthException.notFound("Session not found."));

        // A user may only revoke their own sessions. Do not leak existence of
        // other users' sessions — respond identically to "not found".
        if (!session.getUserId().equals(principal.id())) {
            throw AuthException.notFound("Session not found.");
        }

        sessionRepository.delete(session);
        return ResponseEntity.ok(new MessageResponse("Session revoked."));
    }

    /** SHA-256 hash of the caller's refresh-token cookie, or null if absent. */
    private String currentSessionHash(HttpServletRequest http) {
        if (http.getCookies() == null) {
            return null;
        }
        return Arrays.stream(http.getCookies())
                .filter(c -> "refresh_token".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .map(tokenHasher::hash)
                .orElse(null);
    }
}
