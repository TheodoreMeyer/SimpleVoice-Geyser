package io.github.theodoremeyer.simplevoicegeyser.core.proxy;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.UUID;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** Calls the optional Velocity control endpoint without exposing password data. */
public final class ProxyControlClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static volatile HttpClient client;
    private static volatile boolean clientVerifiesSsl = true;

    private ProxyControlClient() {}

    /**
     * Get the shared HTTP client, rebuilding it when SSL verification settings change.
     * @param verifySsl whether TLS certificates should be verified
     * @return configured http client
     */
    private static HttpClient getClient(boolean verifySsl) throws Exception {
        HttpClient cached = client;
        if (cached != null && clientVerifiesSsl == verifySsl) {
            return cached;
        }

        synchronized (ProxyControlClient.class) {
            if (client == null || clientVerifiesSsl != verifySsl) {
                HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(TIMEOUT);
                if (!verifySsl) {
                    TrustManager trustAll = new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    };
                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(null, new TrustManager[] {trustAll}, new SecureRandom());
                    builder.sslContext(sslContext);
                }
                client = builder.build();
                clientVerifiesSsl = verifySsl;
                SvgCore.getLogger().debug("Proxy control HTTP client rebuilt (verify-ssl: " + verifySsl + ")");
            }
            return client;
        }
    }

    public static ProxyPlayerState updatePlayerState(UUID uuid, String username, boolean joined) {
        if (!Boolean.TRUE.equals(SvgCore.getConfig().PROXY_ENABLED.get())) {
            return ProxyPlayerState.unavailable();
        }

        String configuredUrl = SvgCore.getConfig().PROXY_CONTROL_URL.get();
        if (configuredUrl == null || configuredUrl.isBlank()) {
            return ProxyPlayerState.unavailable();
        }

        JSONObject payload = new JSONObject();
        payload.put("uuid", uuid.toString());
        payload.put("username", username);
        payload.put("joined", joined);

        try {
            boolean verifySsl = !Boolean.FALSE.equals(SvgCore.getConfig().PROXY_VERIFY_SSL.get());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(configuredUrl))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("X-SVG-Proxy-Secret", SvgCore.getConfig().PROXY_SHARED_SECRET.get())
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
            HttpResponse<String> response = getClient(verifySsl).send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return ProxyPlayerState.unavailable();
            }

            JSONObject result = new JSONObject(response.body());
            return new ProxyPlayerState(
                    result.optBoolean("passwordSet", false),
                    result.optBoolean("changingServer", false)
            );
        } catch (Exception e) {
            SvgCore.getLogger().debug("Proxy control request failed", e);
            return ProxyPlayerState.unavailable();
        }
    }
}
