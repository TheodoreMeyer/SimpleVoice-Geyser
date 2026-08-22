package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth;

import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Signed short-lived token used by the proxy to authenticate a browser session to a backend.
 */
public final class ProxyAuthToken {

    private static final String HMAC_ALG = "HmacSHA256";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();

    private ProxyAuthToken() {}

    public record Claims(UUID uuid, String username, long expiresAt, String nonce) {}

    public static String create(UUID uuid, String username, String secret, Duration ttl) {
        long now = Instant.now().getEpochSecond();
        JSONObject payload = new JSONObject();
        payload.put("uuid", uuid.toString());
        payload.put("username", username);
        payload.put("iat", now);
        payload.put("exp", now + Math.max(1, ttl.getSeconds()));
        payload.put("nonce", randomNonce());

        String payloadB64 = B64.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        String sigB64 = B64.encodeToString(sign(payloadB64, secret));
        return payloadB64 + "." + sigB64;
    }

    public static Claims validate(String token, String secret) {
        if (token == null || token.isBlank() || secret == null || secret.isBlank()) {
            return null;
        }

        int dot = token.indexOf('.');
        if (dot <= 0 || dot >= token.length() - 1) {
            return null;
        }

        String payloadB64 = token.substring(0, dot);
        String sigB64 = token.substring(dot + 1);

        byte[] expected = sign(payloadB64, secret);
        byte[] actual;
        try {
            actual = B64D.decode(sigB64);
        } catch (IllegalArgumentException e) {
            return null;
        }

        if (!MessageDigest.isEqual(expected, actual)) {
            return null;
        }

        JSONObject payload;
        try {
            String json = new String(B64D.decode(payloadB64), StandardCharsets.UTF_8);
            payload = new JSONObject(json);
        } catch (Exception e) {
            return null;
        }

        long exp = payload.optLong("exp", 0L);
        if (exp <= Instant.now().getEpochSecond()) {
            return null;
        }

        try {
            return new Claims(
                    UUID.fromString(payload.getString("uuid")),
                    payload.optString("username", ""),
                    exp,
                    payload.optString("nonce", "")
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] sign(String payloadB64, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            return mac.doFinal(payloadB64.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign proxy token", e);
        }
    }

    private static String randomNonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return B64.encodeToString(bytes);
    }
}
