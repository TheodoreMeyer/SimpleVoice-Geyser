package io.github.theodoremeyer.simplevoicegeyser.velocity.proxy;

import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Signed short-lived token the proxy issues to browser sessions so they can
 * authenticate against a backend server.
 * <p>
 * This variant additionally enforces single use: every nonce seen by
 * {@link #validate(String, String)} is remembered until its expiry, so a
 * token cannot be replayed.
 */
public final class ProxyAuthToken {

    private static final String HMAC_ALG = "HmacSHA256";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, Long> USED_NONCES = new HashMap<>();

    private ProxyAuthToken() {}

    /**
     * Parsed payload of a validated proxy auth token.
     *
     * @param uuid      UUID of the player the token was issued for
     * @param username  username of the player the token was issued for
     * @param expiresAt epoch second at which the token expires
     * @param nonce     random value unique to this token issuance
     */
    public record Claims(UUID uuid, String username, long expiresAt, String nonce) {}

    /**
     * Create a new signed, short-lived proxy auth token for a player session.
     *
     * @param uuid     UUID of the player to issue the token for
     * @param username username of the player to issue the token for
     * @param secret   shared secret used to sign the token; must match the secret used for validation
     * @param ttl      how long the token stays valid; values below one second are treated as one second
     * @return the signed token as {@code base64url(payload) + "." + base64url(signature)}
     */
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

    /**
     * Validate a signed proxy auth token against the shared secret and mark its
     * nonce as used. A valid token is accepted exactly once; replaying it returns
     * {@code null}. The token is rejected if it is malformed, the signature does
     * not match, its {@code exp} claim is in the past, or its nonce was already seen.
     *
     * @param token  token string produced by {@link #create(UUID, String, String, Duration)}
     * @param secret shared secret the token was signed with
     * @return the parsed claims if the token is valid, unexpired, and unused, otherwise {@code null}
     */
    public static synchronized Claims validate(String token, String secret) {
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
            Claims claims = new Claims(
                    UUID.fromString(payload.getString("uuid")),
                    payload.optString("username", ""),
                    exp,
                    payload.optString("nonce", "")
            );
            if (claims.nonce().isBlank()) {
                return null;
            }
            long now = Instant.now().getEpochSecond();
            Iterator<Map.Entry<String, Long>> iterator = USED_NONCES.entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getValue() <= now) {
                    iterator.remove();
                }
            }
            if (USED_NONCES.containsKey(claims.nonce())) {
                return null;
            }
            USED_NONCES.put(claims.nonce(), exp);
            return claims;
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
