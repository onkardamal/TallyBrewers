package com.securebank.auth.application;

/**
 * Abstraction over outbound transactional email.
 *
 * Business logic depends only on this interface, never on a concrete mail
 * provider, so the SMTP implementation used now can be replaced later with
 * SendGrid, AWS SES, etc. without changing any service code.
 */
public interface EmailSender {

    /**
     * Send an email-verification message containing a link with the given
     * raw (unhashed) verification token.
     */
    void sendVerificationEmail(String toAddress, String recipientName, String verificationToken);

    /**
     * Send an account recovery email containing a link with the given
     * raw (unhashed) verification token.
     */
    void sendRecoveryEmail(String toAddress, String recipientName, String recoveryToken);
}
