package com.securebank.auth.webauthn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.upokecenter.cbor.CBORObject;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * A minimal but REAL software WebAuthn authenticator for tests.
 *
 * This is not a mock: it generates a genuine EC P-256 key pair, builds a
 * spec-compliant CBOR/COSE attestation object with the "none" attestation
 * format, and produces the exact registration-response JSON a browser's
 * navigator.credentials.create() would return. The server-side Yubico library
 * then performs its real verification against this response. This is the same
 * approach a platform/software authenticator uses — the cryptography and
 * encoding are real end to end.
 *
 * It only implements what passkey REGISTRATION needs (Phase 2). Assertion
 * (login) signing will be added when login is implemented.
 */
public final class TestSoftwareAuthenticator {

    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DEC = Base64.getUrlDecoder();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KeyPair keyPair;
    private final byte[] credentialId;

    public TestSoftwareAuthenticator() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
            this.keyPair = generator.generateKeyPair();
            this.credentialId = new byte[32];
            new SecureRandom().nextBytes(this.credentialId);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize test authenticator", e);
        }
    }

    /**
     * Produce a registration-response JSON for the given
     * PublicKeyCredentialCreationOptions JSON (as returned by the server's
     * /passkey/register/start endpoint).
     */
    public String createRegistrationResponse(String creationOptionsJson, String origin) {
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(creationOptionsJson);
            // The server wraps options under "publicKey" (toCredentialsCreateJson()).
            ObjectNode publicKey = (ObjectNode) root.get("publicKey");
            String challengeB64 = publicKey.get("challenge").asText();
            String rpId = publicKey.get("rp").get("id").asText();

            byte[] clientDataJson = buildClientDataJson(challengeB64, origin);
            byte[] authData = buildAuthenticatorData(rpId);
            byte[] attestationObject = buildAttestationObject(authData);

            ObjectNode response = MAPPER.createObjectNode();
            response.put("clientDataJSON", B64URL.encodeToString(clientDataJson));
            response.put("attestationObject", B64URL.encodeToString(attestationObject));

            ObjectNode credential = MAPPER.createObjectNode();
            String credIdB64 = B64URL.encodeToString(credentialId);
            credential.put("type", "public-key");
            credential.put("id", credIdB64);
            credential.put("rawId", credIdB64);
            credential.set("response", response);
            credential.set("clientExtensionResults", MAPPER.createObjectNode());

            return MAPPER.writeValueAsString(credential);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build registration response", e);
        }
    }

    private byte[] buildClientDataJson(String challengeB64, String origin) throws Exception {
        // Re-encode the challenge as base64url without padding (browser format).
        String normalizedChallenge = B64URL.encodeToString(B64URL_DEC.decode(challengeB64));
        ObjectNode clientData = MAPPER.createObjectNode();
        clientData.put("type", "webauthn.create");
        clientData.put("challenge", normalizedChallenge);
        clientData.put("origin", origin);
        clientData.put("crossOrigin", false);
        return MAPPER.writeValueAsBytes(clientData);
    }

    private byte[] buildAuthenticatorData(String rpId) throws Exception {
        byte[] rpIdHash = sha256(rpId.getBytes(StandardCharsets.UTF_8));

        // Flags: UP (0x01) | UV (0x04) | AT (0x40) = 0x45
        byte flags = (byte) (0x01 | 0x04 | 0x40);

        byte[] signCount = new byte[] {0, 0, 0, 0};

        byte[] aaguid = new byte[16]; // all-zero AAGUID for software authenticator

        byte[] credIdLen = new byte[] {
                (byte) ((credentialId.length >> 8) & 0xFF),
                (byte) (credentialId.length & 0xFF)
        };

        byte[] coseKey = buildCoseKey();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(rpIdHash);
        out.write(flags);
        out.write(signCount);
        out.write(aaguid);
        out.write(credIdLen);
        out.write(credentialId);
        out.write(coseKey);
        return out.toByteArray();
    }

    private byte[] buildCoseKey() {
        ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
        byte[] x = toFixedLength(publicKey.getW().getAffineX(), 32);
        byte[] y = toFixedLength(publicKey.getW().getAffineY(), 32);

        // COSE_Key for EC2 / ES256 / P-256:
        //   1  (kty) = 2  (EC2)
        //   3  (alg) = -7 (ES256)
        //  -1  (crv) = 1  (P-256)
        //  -2  (x)   = x coordinate
        //  -3  (y)   = y coordinate
        CBORObject coseKey = CBORObject.NewMap();
        coseKey.Add(1, 2);
        coseKey.Add(3, -7);
        coseKey.Add(-1, 1);
        coseKey.Add(-2, x);
        coseKey.Add(-3, y);
        return coseKey.EncodeToBytes();
    }

    private byte[] buildAttestationObject(byte[] authData) {
        CBORObject attestationObject = CBORObject.NewMap();
        attestationObject.Add("fmt", "none");
        attestationObject.Add("attStmt", CBORObject.NewMap());
        attestationObject.Add("authData", authData);
        return attestationObject.EncodeToBytes();
    }

    private static byte[] toFixedLength(BigInteger value, int length) {
        byte[] raw = value.toByteArray();
        if (raw.length == length) {
            return raw;
        }
        byte[] result = new byte[length];
        if (raw.length > length) {
            // Drop leading sign byte(s).
            System.arraycopy(raw, raw.length - length, result, 0, length);
        } else {
            System.arraycopy(raw, 0, result, length - raw.length, raw.length);
        }
        return result;
    }

    private static byte[] sha256(byte[] input) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }
}
