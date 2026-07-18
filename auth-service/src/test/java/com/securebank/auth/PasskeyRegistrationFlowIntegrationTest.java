package com.securebank.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.securebank.auth.api.dto.MessageResponse;
import com.securebank.auth.api.dto.PasskeyRegisterStartRequest;
import com.securebank.auth.api.dto.RegisterRequest;
import com.securebank.auth.api.dto.VerifyEmailRequest;
import com.securebank.auth.domain.UserStatus;
import com.securebank.auth.infrastructure.persistence.PasskeyRepository;
import com.securebank.auth.infrastructure.persistence.RecoveryCodeRepository;
import com.securebank.auth.infrastructure.persistence.UserRepository;
import com.securebank.auth.webauthn.TestSoftwareAuthenticator;
import jakarta.mail.internet.MimeMessage;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * End-to-end integration test for the passkey registration flow using a REAL
 * software WebAuthn authenticator (real EC P-256 keys, real CBOR/COSE
 * encoding). The Yubico library performs genuine attestation verification —
 * nothing is mocked.
 *
 * Full path exercised: register -> verify email -> passkey/register/start ->
 * (authenticator builds attestation) -> passkey/register -> recovery codes
 * issued once and account becomes ACTIVE.
 */
class PasskeyRegistrationFlowIntegrationTest extends AbstractIntegrationTest {

    @RegisterExtension
    static final GreenMailExtension GREEN_MAIL =
            new GreenMailExtension(ServerSetupTest.SMTP);

    private static final String ORIGIN = "https://localhost:5173";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasskeyRepository passkeyRepository;

    @Autowired
    private RecoveryCodeRepository recoveryCodeRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void fullRegistrationFlow_registersPasskeyAndIssuesRecoveryCodes() throws Exception {
        String email = "frank@example.com";

        // 1. Register + verify email.
        restTemplate.postForEntity("/register",
                new RegisterRequest("Frank Foster", email, null), MessageResponse.class);
        String token = extractToken(email);
        restTemplate.postForEntity("/verify-email",
                new VerifyEmailRequest(token), MessageResponse.class);
        assertThat(userRepository.findByEmail(email).orElseThrow().getStatus())
                .isEqualTo(UserStatus.VERIFIED);

        // 2. Start passkey registration.
        ResponseEntity<JsonNode> startResponse = restTemplate.postForEntity(
                "/passkey/register/start",
                new PasskeyRegisterStartRequest(email),
                JsonNode.class);
        assertThat(startResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String handle = startResponse.getBody().get("handle").asText();
        String creationOptions = startResponse.getBody().get("creationOptions").toString();
        assertThat(handle).isNotBlank();
        assertThat(creationOptions).contains("challenge");

        // 3. Real software authenticator produces the attestation response.
        TestSoftwareAuthenticator authenticator = new TestSoftwareAuthenticator();
        String credentialJson = authenticator.createRegistrationResponse(creationOptions, ORIGIN);

        // 4. Finish passkey registration.
        JsonNode finishBody = mapper.createObjectNode()
                .put("handle", handle)
                .set("credential", mapper.readTree(credentialJson));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> finishResponse = restTemplate.postForEntity(
                "/passkey/register",
                new HttpEntity<>(finishBody, headers),
                JsonNode.class);

        assertThat(finishResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 5. Recovery codes returned exactly once (10 codes).
        JsonNode recoveryCodes = finishResponse.getBody().get("recoveryCodes");
        assertThat(recoveryCodes.isArray()).isTrue();
        assertThat(recoveryCodes.size()).isEqualTo(10);

        // 6. State persisted: passkey stored, recovery codes hashed, user ACTIVE.
        var user = userRepository.findByEmail(email).orElseThrow();
        assertThat(passkeyRepository.existsByUserId(user.getId())).isTrue();
        assertThat(recoveryCodeRepository.findByUserId(user.getId())).hasSize(10);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);

        // Stored recovery codes must be BCrypt hashes, never plaintext.
        recoveryCodeRepository.findByUserId(user.getId()).forEach(rc ->
                assertThat(rc.getCodeHash()).startsWith("$2"));
    }

    @Test
    void passkeyStart_beforeEmailVerified_isRejected() {
        String email = "grace@example.com";
        restTemplate.postForEntity("/register",
                new RegisterRequest("Grace Green", email, null), MessageResponse.class);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/passkey/register/start",
                new PasskeyRegisterStartRequest(email),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void passkeyFinish_withTamperedChallenge_failsVerification() throws Exception {
        String email = "heidi@example.com";
        restTemplate.postForEntity("/register",
                new RegisterRequest("Heidi Hall", email, null), MessageResponse.class);
        String token = extractToken(email);
        restTemplate.postForEntity("/verify-email",
                new VerifyEmailRequest(token), MessageResponse.class);

        ResponseEntity<JsonNode> startResponse = restTemplate.postForEntity(
                "/passkey/register/start",
                new PasskeyRegisterStartRequest(email),
                JsonNode.class);
        String handle = startResponse.getBody().get("handle").asText();

        // Build an attestation against a DIFFERENT (tampered) challenge/options,
        // so verification against the stored challenge must fail.
        String tamperedOptions = startResponse.getBody().get("creationOptions").toString()
                .replaceFirst("\"challenge\":\"[^\"]+\"", "\"challenge\":\"AAAAAAAAAAAAAAAAAAAAAA\"");
        TestSoftwareAuthenticator authenticator = new TestSoftwareAuthenticator();
        String credentialJson = authenticator.createRegistrationResponse(tamperedOptions, ORIGIN);

        JsonNode finishBody = mapper.createObjectNode()
                .put("handle", handle)
                .set("credential", mapper.readTree(credentialJson));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> finishResponse = restTemplate.postForEntity(
                "/passkey/register",
                new HttpEntity<>(finishBody, headers),
                Map.class);

        assertThat(finishResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String extractToken(String toAddress) throws Exception {
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
        Matcher matcher = TOKEN_PATTERN.matcher(target.getContent().toString());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
