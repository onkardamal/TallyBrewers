package com.securebank.auth.infrastructure.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.securebank.auth.config.SecureBankProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class JwtTokenProvider {

    private final SecureBankProperties properties;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public JwtTokenProvider(SecureBankProperties properties) {
        this.properties = properties;
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT Secret key cannot be null or empty.");
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm)
                .withIssuer(properties.getJwt().getIssuer())
                .withAudience(properties.getJwt().getAudience())
                .build();
    }

    /**
     * Generate a signed JWT access token for a user.
     *
     * @param userId the user ID
     * @param email  the user's email address
     * @param name   the user's name
     * @return the serialized JWT token string
     */
    public String generateAccessToken(Long userId, String email, String name) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getJwt().getAccessTokenTtlMinutes(), ChronoUnit.MINUTES);

        return JWT.create()
                .withSubject(email)
                .withIssuer(properties.getJwt().getIssuer())
                .withAudience(properties.getJwt().getAudience())
                .withClaim("userId", userId)
                .withClaim("name", name)
                .withIssuedAt(now)
                .withExpiresAt(expiresAt)
                .sign(algorithm);
    }

    /**
     * Validate and decode a JWT token.
     *
     * @param token the serialized JWT token string
     * @return the decoded JWT
     * @throws JWTVerificationException if signature is invalid or token has expired
     */
    public DecodedJWT validateToken(String token) throws JWTVerificationException {
        return verifier.verify(token);
    }
}
