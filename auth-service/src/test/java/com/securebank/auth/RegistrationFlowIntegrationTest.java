package com.securebank.auth;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.securebank.auth.api.dto.MessageResponse;
import com.securebank.auth.api.dto.RegisterRequest;
import com.securebank.auth.api.dto.ResendVerificationRequest;
import com.securebank.auth.api.dto.VerifyEmailRequest;
import com.securebank.auth.domain.User;
import com.securebank.auth.domain.UserStatus;
import com.securebank.auth.infrastructure.persistence.EmailVerificationRepository;
import com.securebank.auth.infrastructure.persistence.UserRepository;
import jakarta.mail.internet.MimeMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Integration tests for the registration + email verification flow.
 *
 * Uses a real PostgreSQL (Testcontainers) and a real in-process SMTP server
 * (GreenMail). Emails are actually sent and read back to extract the raw
 * verification token — nothing is mocked.
 */
class RegistrationFlowIntegrationTest extends AbstractIntegrationTest {

    @RegisterExtension
    static final GreenMailExtension GREEN_MAIL =
            new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)");

    @Test
    void register_thenVerifyEmail_marksUserVerified() throws Exception {
        String email = "alice@example.com";
        ResponseEntity<MessageResponse> registerResponse = restTemplate.postForEntity(
                "/register", new RegisterRequest("Alice Adams", email, null), MessageResponse.class);

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);

        String token = extractTokenFromLatestEmail(email);

        ResponseEntity<MessageResponse> verifyResponse = restTemplate.postForEntity(
                "/verify-email", new VerifyEmailRequest(token), MessageResponse.class);

        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userRepository.findByEmail(email).orElseThrow().getStatus())
                .isEqualTo(UserStatus.VERIFIED);
    }

    @Test
    void verifyEmail_isSingleUse() throws Exception {
        String email = "bob@example.com";
        restTemplate.postForEntity("/register",
                new RegisterRequest("Bob Brown", email, null), MessageResponse.class);
        String token = extractTokenFromLatestEmail(email);

        ResponseEntity<MessageResponse> first = restTemplate.postForEntity(
                "/verify-email", new VerifyEmailRequest(token), MessageResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second attempt with the same token must fail.
        ResponseEntity<MessageResponse> second = restTemplate.postForEntity(
                "/verify-email", new VerifyEmailRequest(token), MessageResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void verifyEmail_rejectsUnknownToken() {
        ResponseEntity<MessageResponse> response = restTemplate.postForEntity(
                "/verify-email", new VerifyEmailRequest("not-a-real-token"), MessageResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void register_duplicateEmail_doesNotCreateSecondUserAndDoesNotLeak() {
        String email = "carol@example.com";
        restTemplate.postForEntity("/register",
                new RegisterRequest("Carol First", email, null), MessageResponse.class);

        ResponseEntity<MessageResponse> duplicate = restTemplate.postForEntity("/register",
                new RegisterRequest("Carol Second", email, null), MessageResponse.class);

        // Same generic 201 response — no leak that the email already exists.
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(userRepository.findByEmail(email).orElseThrow().getName())
                .isEqualTo("Carol First");
    }

    @Test
    void resendVerification_invalidatesPreviousToken() throws Exception {
        String email = "dave@example.com";
        restTemplate.postForEntity("/register",
                new RegisterRequest("Dave Davis", email, null), MessageResponse.class);
        String firstToken = extractTokenFromLatestEmail(email);

        // Resend issues a new token and invalidates the first.
        ResponseEntity<MessageResponse> resend = restTemplate.postForEntity(
                "/verify-email/resend", new ResendVerificationRequest(email), MessageResponse.class);
        assertThat(resend.getStatusCode()).isEqualTo(HttpStatus.OK);

        String secondToken = extractTokenFromLatestEmail(email);
        assertThat(secondToken).isNotEqualTo(firstToken);

        // Old token no longer works.
        ResponseEntity<MessageResponse> oldTokenAttempt = restTemplate.postForEntity(
                "/verify-email", new VerifyEmailRequest(firstToken), MessageResponse.class);
        assertThat(oldTokenAttempt.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // New token works.
        ResponseEntity<MessageResponse> newTokenAttempt = restTemplate.postForEntity(
                "/verify-email", new VerifyEmailRequest(secondToken), MessageResponse.class);
        assertThat(newTokenAttempt.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void resendVerification_isRateLimited() {
        String email = "erin@example.com";
        restTemplate.postForEntity("/register",
                new RegisterRequest("Erin Evans", email, null), MessageResponse.class);

        ResponseEntity<MessageResponse> first = restTemplate.postForEntity(
                "/verify-email/resend", new ResendVerificationRequest(email), MessageResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Immediate second resend within the cooldown window is rejected.
        ResponseEntity<MessageResponse> second = restTemplate.postForEntity(
                "/verify-email/resend", new ResendVerificationRequest(email), MessageResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private String extractTokenFromLatestEmail(String toAddress) throws Exception {
        await().atMost(5, SECONDS).until(() -> GREEN_MAIL.getReceivedMessages().length > 0);

        MimeMessage[] messages = GREEN_MAIL.getReceivedMessages();
        // Find the most recent message addressed to this recipient.
        MimeMessage target = null;
        for (int i = messages.length - 1; i >= 0; i--) {
            if (messages[i].getAllRecipients()[0].toString().contains(toAddress)) {
                target = messages[i];
                break;
            }
        }
        assertThat(target).as("verification email for %s", toAddress).isNotNull();

        String body = target.getContent().toString();
        Matcher matcher = TOKEN_PATTERN.matcher(body);
        assertThat(matcher.find()).as("token present in email body").isTrue();
        return matcher.group(1);
    }
}
