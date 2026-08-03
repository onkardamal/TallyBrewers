package com.securebank.auth.infrastructure.email;

import com.securebank.auth.application.EmailSender;
import com.securebank.auth.config.SecureBankProperties;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * HTTP implementation of {@link EmailSender} using Brevo's REST API.
 * This is used to bypass outbound SMTP port blocking on hosting providers like Railway.
 */
@Component
@ConditionalOnProperty(name = "securebank.mail.provider", havingValue = "brevo-http")
public class BrevoHttpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(BrevoHttpEmailSender.class);
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private final HttpClient httpClient;
    private final SecureBankProperties properties;
    private final String apiKey;

    public BrevoHttpEmailSender(
            SecureBankProperties properties,
            @Value("${BREVO_API_KEY:}") String apiKey) {
        this.properties = properties;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();
    }

    @Override
    public void sendVerificationEmail(String toAddress, String recipientName, String verificationToken) {
        String link = properties.getMail().getVerificationUrlBase()
                + "?token="
                + URLEncoder.encode(verificationToken, StandardCharsets.UTF_8);

        String htmlContent = "Hi " + recipientName + ",<br><br>"
                + "Please verify your email address to continue setting up your SecureBank account:<br><br>"
                + "<a href=\"" + link + "\">" + link + "</a><br><br>"
                + "This link expires in 24 hours. If you did not create a SecureBank account, you can safely ignore this email.";

        sendEmail(toAddress, recipientName, "Verify your SecureBank email address", htmlContent);
    }

    @Override
    public void sendRecoveryEmail(String toAddress, String recipientName, String recoveryToken) {
        String base = properties.getMail().getVerificationUrlBase();
        String recoveryBase = base.endsWith("/verify-email") ? base.replace("/verify-email", "/recover") : base + "/recover";
        String link = recoveryBase
                + "?token="
                + URLEncoder.encode(recoveryToken, StandardCharsets.UTF_8)
                + "&email="
                + URLEncoder.encode(toAddress, StandardCharsets.UTF_8);

        String htmlContent = "Hi " + recipientName + ",<br><br>"
                + "You requested to recover your SecureBank account. "
                + "Please use the link below to verify your email and enter your recovery code:<br><br>"
                + "<a href=\"" + link + "\">" + link + "</a><br><br>"
                + "This link expires in 24 hours. If you did not request account recovery, you can safely ignore this email.";

        sendEmail(toAddress, recipientName, "Recover your SecureBank account", htmlContent);
    }

    @Override
    public void sendStepUpCode(String toAddress, String recipientName, String code) {
        String htmlContent = "Hi " + recipientName + ",<br><br>"
                + "We noticed a sign-in to your SecureBank account from a new device. "
                + "To confirm it's you, enter this verification code:<br><br>"
                + "<b>" + code + "</b><br><br>"
                + "This code expires in 10 minutes. If this wasn't you, do not enter the code.";

        sendEmail(toAddress, recipientName, "Your SecureBank verification code: " + code, htmlContent);
    }

    private void sendEmail(String toAddress, String toName, String subject, String htmlContent) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new org.springframework.mail.MailPreparationException("Brevo API key is not configured");
        }

        try {
            // Build the JSON payload manually
            String jsonPayload = "{"
                    + "\"sender\":{\"name\":\"SecureBank\",\"email\":\"" + properties.getMail().getFromAddress() + "\"},"
                    + "\"to\":[{\"email\":\"" + toAddress + "\",\"name\":\"" + toName + "\"}],"
                    + "\"subject\":\"" + escapeJson(subject) + "\","
                    + "\"htmlContent\":\"" + escapeJson(htmlContent) + "\""
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BREVO_API_URL))
                    .header("api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            log.info("Sending email to {} via Brevo HTTP API", toAddress);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Failed to send email via Brevo HTTP API. Status: {}, Response: {}", response.statusCode(), response.body());
                throw new org.springframework.mail.MailSendException("Brevo HTTP API returned status " + response.statusCode());
            }

            log.info("Email sent successfully to {}", toAddress);
        } catch (Exception e) {
            log.error("Exception occurred while sending email via Brevo HTTP", e);
            throw new org.springframework.mail.MailSendException("Error sending email via Brevo HTTP", e);
        }
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
