package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility;

import io.github.theodoremeyer.simplevoicegeyser.core.BuildInfo;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientCompatibilityValidatorTest {

    private static final String SERVER_VERSION = BuildInfo.PROJECT_VERSION;//"0.1.3";
    private static final String BUILD_ID = BuildInfo.BUILD_ID;//"server-build";
    private static final ClientTypePolicy ALLOW_ALL =
            new ClientTypePolicy(true, List.of());

    @Test
    void acceptsLegacyBrowserJoinWithMatchingBuild() {
        ClientCompatibilityResult result = validate(new JSONObject()
                .put("type", "join")
                .put("username", "PlayerName")
                .put("password", "secret")
                .put("build", BUILD_ID));

        assertTrue(result.accepted());
        assertEquals(ClientIdentity.web(SERVER_VERSION, BUILD_ID), result.identity());
    }

    @Test
    void rejectsLegacyBrowserJoinWithoutBuildUsingExistingUpdateRequiredResult() {
        ClientCompatibilityResult result = validate(new JSONObject()
                .put("type", "join")
                .put("username", "PlayerName")
                .put("password", "secret"));

        assertUpdateRequired(result, "Client missing build id. Update required.");
    }

    @Test
    void rejectsLegacyBrowserJoinWithMismatchedBuildUsingExistingUpdateRequiredResult() {
        ClientCompatibilityResult result = validate(new JSONObject()
                .put("type", "join")
                .put("username", "PlayerName")
                .put("password", "secret")
                .put("build", "other-build"));

        assertUpdateRequired(result, "Outdated client. Please refresh.");
    }

    @Test
    void acceptsWebClientTypeWithMatchingBuild() {
        ClientCompatibilityResult result = validate(joinWithClientType(new JSONObject()
                .put("type", "Web")
                .put("serverVersion", SERVER_VERSION)
                .put("serverBuild", BUILD_ID)
                .put("protocolVersion", 3)));

        assertTrue(result.accepted());
        assertEquals(ClientIdentity.web(SERVER_VERSION, BUILD_ID), result.identity());
    }

    @Test
    void rejectsWebClientTypeMissingProtocolVersion() {
        ClientCompatibilityResult result = validate(joinWithClientType(new JSONObject()
                .put("type", "Web")
                .put("serverVersion", SERVER_VERSION)
                .put("serverBuild", BUILD_ID)));

        assertFalse(result.accepted());
        assertEquals("Outdated client protocol. Please refresh.", result.message());
        assertEquals(ConnectionStates.DisconnectCodes.OUTDATED_CLIENT.getCode(), result.closeCode());
        assertEquals("protocol_unsupported", result.closeReason());
    }

    @Test
    void rejectsWebClientTypeWithOldProtocolVersion() {
        ClientCompatibilityResult result = validate(joinWithClientType(new JSONObject()
                .put("type", "Web")
                .put("serverVersion", SERVER_VERSION)
                .put("serverBuild", BUILD_ID)
                .put("protocolVersion", 2)));

        assertFalse(result.accepted());
        assertEquals("Outdated client protocol. Please refresh.", result.message());
        assertEquals("protocol_unsupported", result.closeReason());
    }

    @Test
    void rejectsWebClientTypeWithMismatchedBuild() {
        ClientCompatibilityResult result = validate(joinWithClientType(new JSONObject()
                .put("type", "Web")
                .put("serverVersion", SERVER_VERSION)
                .put("serverBuild", "other-build")
                .put("protocolVersion", 3)));

        assertUpdateRequired(result, "Outdated client. Please refresh.");
    }

    @Test
    void rejectsWebClientTypeWithoutServerVersionAsInvalidClientInfo() {
        ClientCompatibilityResult result = validate(joinWithClientType(new JSONObject()
                .put("type", "Web")
                .put("serverBuild", BUILD_ID)
                .put("protocolVersion", 3)));

        assertInvalidClientInfo(result);
    }

    @Test
    void acceptsSvgAppClientTypeWithoutBrowserBuild() {
        ClientCompatibilityResult result = validate(joinWithClientType(new JSONObject()
                .put("type", "Svg-App")
                .put("serverVersion", SERVER_VERSION)));

        assertTrue(result.accepted());
        assertEquals(ClientIdentity.svgApp(SERVER_VERSION, ""), result.identity());
    }

    @Test
    void rejectsSvgAppServerVersionMismatchAsAppProtocolUnsupported() {
        ClientCompatibilityResult result = validate(joinWithClientType(new JSONObject()
                .put("type", "Svg-App")
                .put("serverVersion", "0.1.1")));

        assertFalse(result.accepted());
        assertEquals("This app version is not compatible with this server. Update the app.", result.message());
        assertEquals(ConnectionStates.DisconnectCodes.OUTDATED_CLIENT.getCode(), result.closeCode());
        assertEquals("app_protocol_unsupported", result.closeReason());
    }

    @Test
    void rejectsNonObjectClientTypeAsInvalidClientInfo() {
        assertInvalidClientInfo(validate(new JSONObject()
                .put("type", "join")
                .put("username", "PlayerName")
                .put("password", "secret")
                .put("clientType", "Svg-App")));
    }

    @Test
    void rejectsMissingOrBlankClientTypeAsInvalidClientInfo() {
        assertInvalidClientInfo(validate(joinWithClientType(new JSONObject()
                .put("serverVersion", SERVER_VERSION))));

        assertInvalidClientInfo(validate(joinWithClientType(new JSONObject()
                .put("type", "   ")
                .put("serverVersion", SERVER_VERSION))));
    }

    @Test
    void rejectsUnknownClientTypeAsUnsupportedClientType() {
        ClientCompatibilityResult result = validate(joinWithClientType(new JSONObject()
                .put("type", "iOS")
                .put("serverVersion", SERVER_VERSION)));

        assertUnsupportedClientType(result);
    }

    @Test
    void rejectsOldAndroidClientMetadataAsInvalidClientInfo() {
        ClientCompatibilityResult result = validate(new JSONObject()
                .put("type", "join")
                .put("username", "PlayerName")
                .put("password", "secret")
                .put("client", new JSONObject()
                        .put("kind", "android")
                        .put("version", "1.0.0")
                        .put("protocol", 1)));

        assertInvalidClientInfo(result);
    }

    @Test
    void blacklistRejectsListedClientType() {
        ClientCompatibilityResult result = validate(
                joinWithClientType(new JSONObject()
                        .put("type", "Svg-App")
                        .put("serverVersion", SERVER_VERSION)),
                new ClientTypePolicy(true, List.of("Svg-App"))
        );

        assertUnsupportedClientType(result);
    }

    @Test
    void whitelistAcceptsOnlyListedClientTypes() {
        ClientCompatibilityResult accepted = validate(
                joinWithClientType(new JSONObject()
                        .put("type", "Svg-App")
                        .put("serverVersion", SERVER_VERSION)),
                new ClientTypePolicy(false, List.of("Svg-App"))
        );
        ClientCompatibilityResult rejected = validate(
                joinWithClientType(new JSONObject()
                        .put("type", "Web")
                        .put("serverVersion", SERVER_VERSION)
                        .put("serverBuild", BUILD_ID)
                        .put("protocolVersion", 3)),
                new ClientTypePolicy(false, List.of("Svg-App"))
        );

        assertTrue(accepted.accepted());
        assertUnsupportedClientType(rejected);
    }

    private static ClientCompatibilityResult validate(JSONObject join) {
        return validate(join, ALLOW_ALL);
    }

    private static ClientCompatibilityResult validate(JSONObject join, ClientTypePolicy policy) {
        return ClientCompatibilityValidator.validate(join, SERVER_VERSION, BUILD_ID, policy);
    }

    private static JSONObject joinWithClientType(JSONObject clientType) {
        return new JSONObject()
                .put("type", "join")
                .put("username", "PlayerName")
                .put("password", "secret")
                .put("clientType", clientType);
    }

    private static void assertInvalidClientInfo(ClientCompatibilityResult result) {
        assertFalse(result.accepted());
        assertEquals("Invalid client information.", result.message());
        assertEquals(ConnectionStates.DisconnectCodes.PACKET_ERROR.getCode(), result.closeCode());
        assertEquals("invalid_client_info", result.closeReason());
    }

    private static void assertUnsupportedClientType(ClientCompatibilityResult result) {
        assertFalse(result.accepted());
        assertEquals("Unsupported client type.", result.message());
        assertEquals(ConnectionStates.DisconnectCodes.PACKET_ERROR.getCode(), result.closeCode());
        assertEquals("unsupported_client_type", result.closeReason());
    }

    private static void assertUpdateRequired(ClientCompatibilityResult result, String message) {
        assertFalse(result.accepted());
        assertEquals(message, result.message());
        assertEquals(ConnectionStates.DisconnectCodes.OUTDATED_CLIENT.getCode(), result.closeCode());
        assertEquals("update_required", result.closeReason());
    }
}
