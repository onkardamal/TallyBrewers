package com.securebank.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.securebank.auth.api.LoginController.LoginResponse;
import com.securebank.auth.api.LoginController.UserDto;
import com.securebank.auth.api.dto.MessageResponse;
import com.securebank.auth.api.dto.PasskeyRegisterStartRequest;
import com.securebank.auth.api.dto.RegisterRequest;
import com.securebank.auth.api.dto.VerifyEmailRequest;
import com.securebank.auth.domain.UserStatus;
import com.securebank.auth.infrastructure.persistence.PasskeyRepository;
import com.securebank.auth.infrastructure.persistence.RecoveryCodeRepository;
import com.securebank.auth.infrastructure.persistence.UserRepository;
import com.securebank.auth.infrastructure.jwt.JwtTokenProvider;
import com.securebank.auth.webauthn.TestSoftwareAuthenticator;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

class LoginAndRecoveryFlowIntegrationTest extends AbstractIntegrationTest {

    @RegisterExtension
    static final GreenMailExtension GREEN_MAIL = new GreenMailExtension(ServerSetupTest.SMTP);

    private static final String ORIGIN = "https://localhost:5173";
    private static final Pattern VERIFY_TOKEN_PATTERN = Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");
    private static final Pattern RECOVER_TOKEN_PATTERN = Pattern.compile("recover\\?token=([A-Za-z0-9_-]+)");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasskeyRepository passkeyRepository;

    @Autowired
    private RecoveryCodeRepository recoveryCodeRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private com.securebank.auth.application.RateLimiter rateLimiter;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testLoginAndSessionManagementFlow() throws Exception {
        String email = "john@example.com";

        // 1. Onboarding (Register -> Verify Email -> Set up Passkey)
        restTemplate.postForEntity("/register", new RegisterRequest("John Doe", email, null), MessageResponse.class);
        String verifyToken = extractToken(email, VERIFY_TOKEN_PATTERN);
        restTemplate.postForEntity("/verify-email", new VerifyEmailRequest(verifyToken), MessageResponse.class);

        ResponseEntity<JsonNode> startRegResponse = restTemplate.postForEntity(
                "/passkey/register/start",
                new PasskeyRegisterStartRequest(email),
                JsonNode.class
        );
        String regHandle = startRegResponse.getBody().get("handle").asText();
        String creationOptions = startRegResponse.getBody().get("creationOptions").toString();

        TestSoftwareAuthenticator authenticator = new TestSoftwareAuthenticator();
        String regCredentialJson = authenticator.createRegistrationResponse(creationOptions, ORIGIN);

        JsonNode finishRegBody = mapper.createObjectNode()
                .put("handle", regHandle)
                .set("credential", mapper.readTree(regCredentialJson));

        HttpHeaders regHeaders = new HttpHeaders();
        regHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> finishRegResponse = restTemplate.postForEntity(
                "/passkey/register",
                new HttpEntity<>(finishRegBody, regHeaders),
                JsonNode.class
        );
        assertThat(finishRegResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Get recovery codes
        JsonNode recoveryCodesNode = finishRegResponse.getBody().get("recoveryCodes");
        assertThat(recoveryCodesNode.size()).isEqualTo(10);
        String recoveryCode = recoveryCodesNode.get(0).asText();

        // 2. Login Flow (Start -> Authenticator assertion -> Verify)
        ResponseEntity<JsonNode> startLoginResponse = restTemplate.postForEntity(
                "/login/start",
                Map.of("email", email),
                JsonNode.class
        );
        assertThat(startLoginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String loginHandle = startLoginResponse.getBody().get("handle").asText();
        String assertionOptions = startLoginResponse.getBody().get("assertionOptionsJson").asText();

        String assertionCredentialJson = authenticator.createAssertionResponse(assertionOptions, ORIGIN);

        JsonNode verifyLoginBody = mapper.createObjectNode()
                .put("handle", loginHandle)
                .set("credential", mapper.readTree(assertionCredentialJson));

        ResponseEntity<LoginResponse> verifyLoginResponse = restTemplate.postForEntity(
                "/login/verify",
                verifyLoginBody,
                LoginResponse.class
        );

        assertThat(verifyLoginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String accessToken = verifyLoginResponse.getBody().accessToken();
        assertThat(accessToken).isNotBlank();

        // Check for refresh token cookie
        String cookieVal = extractCookie(verifyLoginResponse, "refresh_token");
        assertThat(cookieVal).isNotNull().isNotBlank();
        boolean hasHttpOnly = verifyLoginResponse.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(c -> c.contains("refresh_token=") && c.contains("HttpOnly"));
        assertThat(hasHttpOnly).isTrue();

        // 3. Test access to secure endpoint /me
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(accessToken);
        ResponseEntity<UserDto> meResponse = restTemplate.exchange(
                "/me",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                UserDto.class
        );
        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        System.out.println("verifyLoginResponse SET_COOKIE: " + verifyLoginResponse.getHeaders().get(HttpHeaders.SET_COOKIE));
        System.out.println("meResponse SET_COOKIE: " + meResponse.getHeaders().get(HttpHeaders.SET_COOKIE));
        assertThat(meResponse.getBody().email()).isEqualTo(email);

        // 4. Test accessing secure endpoint /me with invalid/expired JWT
        // Create an expired JWT manually using reflection or a fake token helper
        String expiredJwt = com.auth0.jwt.JWT.create()
                .withSubject(email)
                .withClaim("userId", userRepository.findByEmail(email).get().getId())
                .withClaim("name", "John Doe")
                .withExpiresAt(java.util.Date.from(java.time.Instant.now().minusSeconds(10)))
                .sign(com.auth0.jwt.algorithms.Algorithm.HMAC256(propertiesSecret()));

        HttpHeaders expiredHeaders = new HttpHeaders();
        expiredHeaders.setBearerAuth(expiredJwt);
        ResponseEntity<Map> expiredResponse = restTemplate.exchange(
                "/me",
                HttpMethod.GET,
                new HttpEntity<>(expiredHeaders),
                Map.class
        );
        // Spring Security returns 403 or 401 depending on authorization filters
        assertThat(expiredResponse.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);

        // 5. Session Refresh (using refresh_token cookie)
        HttpHeaders refreshHeaders = createCookieAndCsrfHeaders(meResponse, cookieVal);

        // Sleep 1s to ensure JWT iat is different
        Thread.sleep(1000);

        ResponseEntity<LoginResponse> refreshResponse = restTemplate.postForEntity(
                "/session/refresh",
                new HttpEntity<>(refreshHeaders),
                LoginResponse.class
        );
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newAccessToken = refreshResponse.getBody().accessToken();
        assertThat(newAccessToken).isNotBlank();
        assertThat(newAccessToken).isNotEqualTo(accessToken);

        // Assert cookie is rotated (Set-Cookie should be present with new token)
        String newCookieVal = extractCookie(refreshResponse, "refresh_token");
        assertThat(newCookieVal).isNotNull();
        assertThat(newCookieVal).isNotEqualTo(cookieVal);

        // 6. Reuse of rotated refresh token should be rejected (rotation enforcement)
        resetRateLimiter();
        ResponseEntity<Map> reuseResponse = restTemplate.postForEntity(
                "/session/refresh",
                new HttpEntity<>(refreshHeaders), // using the old cookieVal and old CSRF
                Map.class
        );
        assertThat(reuseResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 7. Recovery Flow
        // Start recovery
        restTemplate.postForEntity("/recover/start", Map.of("email", email), MessageResponse.class);
        String recoverToken = extractToken(email, RECOVER_TOKEN_PATTERN);

        // Verify recovery (first attempt with the code)
        ResponseEntity<JsonNode> verifyRecoverResponse = restTemplate.postForEntity(
                "/recover/verify",
                Map.of("email", email, "token", recoverToken, "recoveryCode", recoveryCode),
                JsonNode.class
        );
        assertThat(verifyRecoverResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String recoverHandle = verifyRecoverResponse.getBody().get("handle").asText();
        String recoverCreationOptions = verifyRecoverResponse.getBody().get("creationOptionsJson").asText();

        // Try to reuse the SAME recovery code (must be rejected!)
        resetRateLimiter();
        ResponseEntity<Map> reuseCodeResponse = restTemplate.postForEntity(
                "/recover/verify",
                Map.of("email", email, "token", recoverToken, "recoveryCode", recoveryCode),
                Map.class
        );
        assertThat(reuseCodeResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 8. Complete recovery (Register new passkey)
        TestSoftwareAuthenticator newAuthenticator = new TestSoftwareAuthenticator();
        String recoverCredentialJson = newAuthenticator.createRegistrationResponse(recoverCreationOptions, ORIGIN);

        JsonNode completeRecoverBody = mapper.createObjectNode()
                .put("handle", recoverHandle)
                .set("credential", mapper.readTree(recoverCredentialJson));

        ResponseEntity<JsonNode> completeRecoverResponse = restTemplate.postForEntity(
                "/recover/passkey",
                completeRecoverBody,
                JsonNode.class
        );
        assertThat(completeRecoverResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String recoveryAccessToken = completeRecoverResponse.getBody().get("accessToken").asText();
        assertThat(recoveryAccessToken).isNotBlank();
        assertThat(completeRecoverResponse.getBody().get("recoveryCodes").size()).isEqualTo(10);

        // Verify that the old passkey is revoked by attempting login with old authenticator
        ResponseEntity<JsonNode> oldLoginStart = restTemplate.postForEntity(
                "/login/start",
                Map.of("email", email),
                JsonNode.class
        );
        String oldLoginOptions = oldLoginStart.getBody().get("assertionOptionsJson").asText();
        // Since Yubico finished registration, it checks user's registered credentials.
        // Let's verify that the old credential ID is NOT in the credentials list returned by the server,
        // or that trying to verify assertion with the old authenticator fails.
        String oldAssertionResponse = authenticator.createAssertionResponse(oldLoginOptions, ORIGIN);
        JsonNode oldLoginBody = mapper.createObjectNode()
                .put("handle", oldLoginStart.getBody().get("handle").asText())
                .set("credential", mapper.readTree(oldAssertionResponse));
        ResponseEntity<Map> oldLoginVerify = restTemplate.postForEntity(
                "/login/verify",
                oldLoginBody,
                Map.class
        );
        assertThat(oldLoginVerify.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 9. Logout
        HttpHeaders recoveryAuthHeaders = new HttpHeaders();
        recoveryAuthHeaders.setBearerAuth(recoveryAccessToken);
        ResponseEntity<UserDto> recoveryMeResponse = restTemplate.exchange(
                "/me",
                HttpMethod.GET,
                new HttpEntity<>(recoveryAuthHeaders),
                UserDto.class
        );
        assertThat(recoveryMeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        String recoveryCookieVal = extractCookie(completeRecoverResponse, "refresh_token");
        HttpHeaders logoutHeaders = createCookieAndCsrfHeaders(recoveryMeResponse, recoveryCookieVal);
        ResponseEntity<MessageResponse> logoutResponse = restTemplate.postForEntity(
                "/logout",
                new HttpEntity<>(logoutHeaders),
                MessageResponse.class
        );
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String propertiesSecret() {
        return jwtTokenProvider.getClass().getClassLoader() != null ? "df882d921b36be9f2a9db26e95c102a0a2df362c8e001cf4b63810f279149021" : "df882d921b36be9f2a9db26e95c102a0a2df362c8e001cf4b63810f279149021";
    }

    private String extractToken(String toAddress, Pattern pattern) throws Exception {
        await().atMost(5, SECONDS).until(() -> GREEN_MAIL.getReceivedMessages().length > 0);
        MimeMessage[] messages = GREEN_MAIL.getReceivedMessages();
        MimeMessage target = null;
        for (int i = messages.length - 1; i >= 0; i--) {
            if (messages[i].getAllRecipients()[0].toString().contains(toAddress)) {
                target = messages[i];
                break;
            }
        }
        assertThat(target).isNotNull();
        Matcher matcher = pattern.matcher(target.getContent().toString());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String extractCookie(ResponseEntity<?> response, String name) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies == null) return null;
        for (String cookie : cookies) {
            String[] parts = cookie.split(";");
            if (parts.length > 0) {
                String firstPart = parts[0].trim();
                if (firstPart.startsWith(name + "=")) {
                    return firstPart.substring((name + "=").length());
                }
            }
        }
        return null;
    }

    private HttpHeaders createCookieAndCsrfHeaders(ResponseEntity<?> responseWithCookies, String refreshTokenVal) {
        HttpHeaders headers = new HttpHeaders();
        String csrfToken = extractCookie(responseWithCookies, "XSRF-TOKEN");
        StringBuilder cookieHeader = new StringBuilder();
        if (refreshTokenVal != null) {
            cookieHeader.append("refresh_token=").append(refreshTokenVal);
        }
        if (csrfToken != null) {
            if (cookieHeader.length() > 0) {
                cookieHeader.append("; ");
            }
            cookieHeader.append("XSRF-TOKEN=").append(csrfToken);
            headers.add("X-XSRF-TOKEN", csrfToken);
        }
        if (cookieHeader.length() > 0) {
            headers.add(HttpHeaders.COOKIE, cookieHeader.toString());
        }
        return headers;
    }
    private void resetRateLimiter() {
        Map<?, ?> lastAllowed = (Map<?, ?>) ReflectionTestUtils.getField(rateLimiter, "lastAllowed");
        if (lastAllowed != null) {
            lastAllowed.clear();
        }
    }
}
