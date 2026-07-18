package com.securebank.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Type-safe binding for all "securebank.*" application properties.
 *
 * Centralizing these values means WebAuthn relying party settings, JWT
 * lifetimes, and mail sender details can move from localhost/dev values
 * to production values purely through configuration, with no code changes.
 */
@ConfigurationProperties(prefix = "securebank")
public class SecureBankProperties {

    @NestedConfigurationProperty
    private final WebAuthn webauthn = new WebAuthn();

    @NestedConfigurationProperty
    private final Jwt jwt = new Jwt();

    @NestedConfigurationProperty
    private final Mail mail = new Mail();

    @NestedConfigurationProperty
    private final RateLimit rateLimit = new RateLimit();

    public WebAuthn getWebauthn() {
        return webauthn;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public Mail getMail() {
        return mail;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public static class RateLimit {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class WebAuthn {
        /** Relying Party ID. Must be "localhost" in dev, the real domain in production. */
        private String relyingPartyId;
        /** Human-readable name shown by the browser/authenticator UI. */
        private String relyingPartyName;
        /** Expected origin of the frontend application. */
        private String origin;

        public String getRelyingPartyId() {
            return relyingPartyId;
        }

        public void setRelyingPartyId(String relyingPartyId) {
            this.relyingPartyId = relyingPartyId;
        }

        public String getRelyingPartyName() {
            return relyingPartyName;
        }

        public void setRelyingPartyName(String relyingPartyName) {
            this.relyingPartyName = relyingPartyName;
        }

        public String getOrigin() {
            return origin;
        }

        public void setOrigin(String origin) {
            this.origin = origin;
        }
    }

    public static class Jwt {
        private String secret;
        private int accessTokenTtlMinutes;
        private int refreshTokenTtlDays;
        private String issuer;
        private String audience;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public int getAccessTokenTtlMinutes() {
            return accessTokenTtlMinutes;
        }

        public void setAccessTokenTtlMinutes(int accessTokenTtlMinutes) {
            this.accessTokenTtlMinutes = accessTokenTtlMinutes;
        }

        public int getRefreshTokenTtlDays() {
            return refreshTokenTtlDays;
        }

        public void setRefreshTokenTtlDays(int refreshTokenTtlDays) {
            this.refreshTokenTtlDays = refreshTokenTtlDays;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }
    }

    public static class Mail {
        private String fromAddress;
        /**
         * Base URL of the frontend email-verification page. The raw
         * verification token is appended as a query parameter to build the
         * link placed in the outbound email.
         */
        private String verificationUrlBase;

        public String getFromAddress() {
            return fromAddress;
        }

        public void setFromAddress(String fromAddress) {
            this.fromAddress = fromAddress;
        }

        public String getVerificationUrlBase() {
            return verificationUrlBase;
        }

        public void setVerificationUrlBase(String verificationUrlBase) {
            this.verificationUrlBase = verificationUrlBase;
        }
    }
}
