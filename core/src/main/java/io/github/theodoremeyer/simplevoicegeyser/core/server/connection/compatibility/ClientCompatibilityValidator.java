package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility;

import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
import org.json.JSONObject;

/**
 * Class in charge of validating the compatibility of a client based on its join packet and expected server version/build.
 * It checks for the presence and validity of clientType, type, serverVersion, and build fields, and ensures that the client is compatible with the server's expected version and build.
 * It also checks against a provided ClientTypePolicy to determine if the client type is allowed.
 * The class provides methods to validate both web clients and SVG app clients, returning a ClientCompatibilityResult indicating whether the client is accepted or rejected, along with relevant messages and codes.
 */
public final class ClientCompatibilityValidator {

    private static final String WEB_TYPE = "Web";
    private static final String SVG_APP_TYPE = "Svg-App";

    private static final ClientCompatibilityResult INVALID_CLIENT_INFO =
            ClientCompatibilityResult.rejected(
                    "Invalid client information.",
                    ConnectionStates.DisconnectCodes.PACKET_ERROR.getCode(),
                    "invalid_client_info"
            );

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ClientCompatibilityValidator() {}

    /**
     * Validate the Compatibility of a client based on its join packet and expected server version/build.
     * Svg-App must match {@code expectedServerVersion} exactly ({@code requireLatest} defaults to true).
     *
     * @param join join packet
     * @param expectedServerVersion expected version
     * @param expectedBrowserBuild expected build
     * @param policy type policy
     * @return Result
     */
    public static ClientCompatibilityResult validate(
            JSONObject join,
            String expectedServerVersion,
            String expectedBrowserBuild,
            ClientTypePolicy policy
    ) {
        return validate(join, expectedServerVersion, expectedBrowserBuild, policy, true);
    }

    /**
     * Validate client compatibility.
     *
     * @param join join packet
     * @param expectedServerVersion expected plugin version
     * @param expectedBrowserBuild expected web build id
     * @param policy type policy
     * @param requireLatest when true, Svg-App must match expected version exactly;
     *                      when false, same major.minor (any patch) is accepted
     * @return result
     */
    public static ClientCompatibilityResult validate(
            JSONObject join,
            String expectedServerVersion,
            String expectedBrowserBuild,
            ClientTypePolicy policy,
            boolean requireLatest
    ) {
        if (!join.has("clientType")) {
            if (join.has("client")) {
                return INVALID_CLIENT_INFO;
            }
            // TODO 0.1.3: Remove this browser build fallback once Web always sends clientType.serverBuild.
            return validateBrowser(join, expectedServerVersion, expectedBrowserBuild, policy, null);
        }

        Object rawClientType = join.opt("clientType");
        if (!(rawClientType instanceof JSONObject clientType)) {
            return INVALID_CLIENT_INFO;
        }

        Object rawType = clientType.opt("type");
        if (!(rawType instanceof String typeValue) || typeValue.trim().isEmpty()) {
            return INVALID_CLIENT_INFO;
        }

        String type = typeValue.trim();
        if (!WEB_TYPE.equals(type) && !SVG_APP_TYPE.equals(type)) {
            return ClientCompatibilityResult.rejected(
                    "Unsupported client type.",
                    ConnectionStates.DisconnectCodes.PACKET_ERROR.getCode(),
                    "unsupported_client_type"
            );
        }

        if (policy != null && !policy.allows(type)) {
            return ClientCompatibilityResult.rejected(
                    "Unsupported client type.",
                    ConnectionStates.DisconnectCodes.PACKET_ERROR.getCode(),
                    "unsupported_client_type"
            );
        }

        return switch (type) {
            case WEB_TYPE -> validateBrowser(join, expectedServerVersion, expectedBrowserBuild, policy, clientType);
            case SVG_APP_TYPE -> validateSvgApp(clientType, expectedServerVersion, requireLatest);
            default -> throw new IllegalStateException("Unexpected client type: " + type);
        };
    }

    private static ClientCompatibilityResult validateBrowser(
            JSONObject join,
            String expectedServerVersion,
            String expectedBrowserBuild,
            ClientTypePolicy policy,
            JSONObject clientType
    ) {
        if (policy != null && !policy.allows(WEB_TYPE)) {
            return ClientCompatibilityResult.rejected(
                    "Unsupported client type.",
                    ConnectionStates.DisconnectCodes.PACKET_ERROR.getCode(),
                    "unsupported_client_type"
            );
        }

        String clientBuild = clientType == null
                ? join.optString("build", "")
                : clientType.optString("serverBuild", "");

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

        String serverVersion = expectedServerVersion;
        if (clientType != null) {
            Object rawServerVersion = clientType.opt("serverVersion");
            if (!(rawServerVersion instanceof String version) || version.trim().isEmpty()) {
                return INVALID_CLIENT_INFO;
            }
            serverVersion = version.trim();
        }

        return ClientCompatibilityResult.accepted(ClientIdentity.web(serverVersion, clientBuild));
    }

    private static ClientCompatibilityResult validateSvgApp(
            JSONObject clientType,
            String expectedServerVersion,
            boolean requireLatest
    ) {
        Object rawServerVersion = clientType.opt("serverVersion");
        if (!(rawServerVersion instanceof String serverVersion) || serverVersion.trim().isEmpty()) {
            return INVALID_CLIENT_INFO;
        }

        String trimmed = serverVersion.trim();
        String expected = expectedServerVersion == null ? "" : expectedServerVersion.trim();
        boolean compatible = requireLatest
                ? expected.equals(trimmed)
                : sameMajorMinor(expected, trimmed);

        if (!compatible) {
            return ClientCompatibilityResult.rejected(
                    "This app version is not compatible with this server. Update the app.",
                    ConnectionStates.DisconnectCodes.OUTDATED_CLIENT.getCode(),
                    "app_protocol_unsupported"
            );
        }

        return ClientCompatibilityResult.accepted(
                ClientIdentity.svgApp(trimmed, clientType.optString("serverBuild", ""))
        );
    }

    /**
     * Compares Major.minor (ignores patch and any -SNAPSHOT / -fork suffix on either side).
     */
    static boolean sameMajorMinor(String expected, String actual) {
        String left = majorMinor(expected);
        String right = majorMinor(actual);
        return !left.isEmpty() && left.equals(right);
    }

    private static String majorMinor(String version) {
        if (version == null || version.isBlank()) {
            return "";
        }
        String core = version.trim();
        int dash = core.indexOf('-');
        if (dash >= 0) {
            core = core.substring(0, dash);
        }
        String[] parts = core.split("\\.");
        if (parts.length < 2) {
            return core;
        }
        return parts[0] + "." + parts[1];
    }
}
