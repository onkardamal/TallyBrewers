package com.securebank.auth.infrastructure.webauthn;

import com.securebank.auth.config.SecureBankProperties;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;

import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the Yubico {@link RelyingParty}, the central object that performs all
 * WebAuthn verification logic.
 *
 * The Relying Party ID, display name, and expected origin all come from
 * configuration (SecureBankProperties), so moving from localhost (dev) to a
 * real production domain requires only a configuration change — no code change.
 */
@Configuration
public class WebAuthnConfig {

    @Bean
    public RelyingParty relyingParty(SecureBankProperties properties,
                                     CredentialRepository credentialRepository) {
        SecureBankProperties.WebAuthn cfg = properties.getWebauthn();

        RelyingPartyIdentity identity = RelyingPartyIdentity.builder()
                .id(cfg.getRelyingPartyId())
                .name(cfg.getRelyingPartyName())
                .build();

        return RelyingParty.builder()
                .identity(identity)
                .credentialRepository(credentialRepository)
                .origins(Set.of(cfg.getOrigin()))
                .allowOriginPort(true)
                .build();
    }
}
