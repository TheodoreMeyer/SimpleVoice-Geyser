package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility;

import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
import org.json.JSONObject;

/**
 * Validates the websocket join compatibility metadata before authentication.
 */
public final class ClientCompatibilityValidator {

    private static final String ANDROID_KIND = "android";
    private static final int ANDROID_PROTOCOL = 1;

    private static final ClientCompatibilityResult INVALID_CLIENT_INFO =
            ClientCompatibilityResult.rejected(
                    "Invalid Android client information.",
                    ConnectionStates.DisconnectCodes.PACKET_ERROR.getCode(),
                    "invalid_client_info"
            );

    private ClientCompatibilityValidator() {}

    public static ClientCompatibilityResult validate(JSONObject join, String expectedBrowserBuild) {
        if (!join.has("client")) {
            return validateBrowser(join, expectedBrowserBuild);
        }

        Object rawClient = join.opt("client");
        if (!(rawClient instanceof JSONObject client)) {
            return INVALID_CLIENT_INFO;
        }

        Object rawKind = client.opt("kind");
        if (!(rawKind instanceof String kind) || kind.trim().isEmpty()) {
            return INVALID_CLIENT_INFO;
        }

        if (!ANDROID_KIND.equals(kind.trim())) {
            return ClientCompatibilityResult.rejected(
                    "Unsupported client type.",
                    ConnectionStates.DisconnectCodes.PACKET_ERROR.getCode(),
                    "unsupported_client_type"
            );
        }

        return validateAndroid(client);
    }

    private static ClientCompatibilityResult validateBrowser(JSONObject join, String expectedBrowserBuild) {
        String clientBuild = join.optString("build", "");

        if (clientBuild.isEmpty()) {
            return ClientCompatibilityResult.rejected(
                    "Client missing build id. Update required.",
                    ConnectionStates.DisconnectCodes.OUTDATED_CLIENT.getCode(),
                    "update_required"
            );
        }

        if (!expectedBrowserBuild.equals(clientBuild)) {
            return ClientCompatibilityResult.rejected(
                    "Outdated client. Please refresh.",
                    ConnectionStates.DisconnectCodes.OUTDATED_CLIENT.getCode(),
                    "update_required"
            );
        }

        return ClientCompatibilityResult.accepted(ClientIdentity.browser());
    }

    private static ClientCompatibilityResult validateAndroid(JSONObject client) {
        Object rawVersion = client.opt("version");
        if (!(rawVersion instanceof String version) || version.trim().isEmpty()) {
            return INVALID_CLIENT_INFO;
        }

        Integer protocol = integerValue(client.opt("protocol"));
        if (protocol == null) {
            return INVALID_CLIENT_INFO;
        }

        if (protocol != ANDROID_PROTOCOL) {
            return ClientCompatibilityResult.rejected(
                    "This app version is not compatible with this server. Update the app.",
                    ConnectionStates.DisconnectCodes.OUTDATED_CLIENT.getCode(),
                    "app_protocol_unsupported"
            );
        }

        return ClientCompatibilityResult.accepted(
                new ClientIdentity(ANDROID_KIND, version.trim(), protocol)
        );
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }

        if (value instanceof Long longValue
                && longValue >= Integer.MIN_VALUE
                && longValue <= Integer.MAX_VALUE) {
            return longValue.intValue();
        }

        return null;
    }
}
