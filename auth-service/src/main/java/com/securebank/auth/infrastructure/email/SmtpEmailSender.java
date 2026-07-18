package com.securebank.auth.infrastructure.email;

import com.securebank.auth.application.EmailSender;
import com.securebank.auth.config.SecureBankProperties;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP implementation of {@link EmailSender} using Spring's JavaMailSender.
 *
 * In development this points at a local/Gmail SMTP server; in tests it points
 * at an in-process GreenMail server. The SMTP host/port/credentials are all
 * configuration-driven (see application.yml), so no provider details are
 * hard-coded here.
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final SecureBankProperties properties;

    public SmtpEmailSender(JavaMailSender mailSender, SecureBankProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendVerificationEmail(String toAddress, String recipientName, String verificationToken) {
        String link = properties.getMail().getVerificationUrlBase()
                + "?token="
                + URLEncoder.encode(verificationToken, StandardCharsets.UTF_8);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMail().getFromAddress());
        message.setTo(toAddress);
        message.setSubject("Verify your SecureBank email address");
        message.setText(
                "Hi " + recipientName + ",\n\n"
                        + "Please verify your email address to continue setting up your "
                        + "SecureBank account:\n\n"
                        + link + "\n\n"
                        + "This link expires in 24 hours. If you did not create a "
                        + "SecureBank account, you can safely ignore this email.\n");

        mailSender.send(message);
    }
}
