package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility;

import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientCompatibilityValidatorTest {

    private static final String BUILD_ID = "server-build";

    @Test
    void acceptsBrowserJoinWithMatchingBuild() {
        JSONObject join = new JSONObject()
                .put("type", "join")
                .put("username", "PlayerName")
                .put("password", "secret")
                .put("build", BUILD_ID);

        ClientCompatibilityResult result = ClientCompatibilityValidator.validate(join, BUILD_ID);

        assertTrue(result.accepted());
        assertEquals(ClientIdentity.browser(), result.identity());
    }

    @Test
    void rejectsBrowserJoinWithoutBuildUsingExistingUpdateRequiredResult() {
        JSONObject join = new JSONObject()
                .put("type", "join")
                .put("username", "PlayerName")
                .put("password", "secret");

        ClientCompatibilityResult result = ClientCompatibilityValidator.validate(join, BUILD_ID);

        assertFalse(result.accepted());
        assertEquals("Client missing build id. Update required.", result.message());
        assertEquals(ConnectionStates.DisconnectCodes.OUTDATED_CLIENT.getCode(), result.closeCode());
        assertEquals("update_required", result.closeReason());
    }

    @Test
    void rejectsBrowserJoinWithMismatchedBuildUsingExistingUpdateRequiredResult() {
        JSONObject join = new JSONObject()
                .put("type", "join")
                .put("username", "PlayerName")
                .put("password", "secret")
                .put("build", "other-build");

        ClientCompatibilityResult result = ClientCompatibilityValidator.validate(join, BUILD_ID);

        assertFalse(result.accepted());
        assertEquals("Outdated client. Please refresh.", result.message());
        assertEquals(ConnectionStates.DisconnectCodes.OUTDATED_CLIENT.getCode(), result.closeCode());
        assertEquals("update_required", result.closeReason());
    }

    @Test
    void acceptsAndroidJoinWithProtocolOneWithoutBuild() {
        JSONObject join = androidJoin(1);

        ClientCompatibilityResult result = ClientCompatibilityValidator.validate(join, BUILD_ID);

        assertTrue(result.accepted());
        assertEquals(new ClientIdentity("android", "1.0.0", 1), result.identity());
    }

    @Test
    void androidClientMetadataOverridesExtraMismatchedBuild() {
        JSONObject join = androidJoin(1).put("build", "wrong-browser-build");

        ClientCompatibilityResult result = ClientCompatibilityValidator.validate(join, BUILD_ID);

        assertTrue(result.accepted());
        assertEquals(new ClientIdentity("android", "1.0.0", 1), result.identity());
    }

    @Test
    void rejectsNonObjectClientMetadataAsInvalidClientInfo() {
        JSONObject join = new JSONObject()
                .put("type", "join")
                .put("username", "PlayerName")
                .put("password", "secret")
                .put("client", "android");

        ClientCompatibilityResult result = ClientCompatibilityValidator.validate(join, BUILD_ID);

        assertInvalidClientInfo(result);
    }

    @Test
    void rejectsMissingOrBlankAndroidVersionAsInvalidClientInfo() {
        assertInvalidClientInfo(ClientCompatibilityValidator.validate(
                androidJoin(1).put("client", new JSONObject()
                        .put("kind", "android")
                        .put("protocol", 1)),
                BUILD_ID
        ));

        assertInvalidClientInfo(ClientCompatibilityValidator.validate(
                androidJoin(1).put("client", new JSONObject()
                        .put("kind", "android")
                        .put("version", "   ")
                        .put("protocol", 1)),
                BUILD_ID
        ));
    }

    @Test
    void rejectsMissingOrNonIntegerAndroidProtocolAsInvalidClientInfo() {
        assertInvalidClientInfo(ClientCompatibilityValidator.validate(
                androidJoin(1).put("client", new JSONObject()
                        .put("kind", "android")
                        .put("version", "1.0.0")),
                BUILD_ID
        ));

        assertInvalidClientInfo(ClientCompatibilityValidator.validate(
                androidJoin(1).put("client", new JSONObject()
                        .put("kind", "android")
                        .put("version", "1.0.0")
                        .put("protocol", "1")),
                BUILD_ID
        ));

        assertInvalidClientInfo(ClientCompatibilityValidator.validate(
                androidJoin(1).put("client", new JSONObject()
                        .put("kind", "android")
                        .put("version", "1.0.0")
                        .put("protocol", 1.5)),
                BUILD_ID
        ));
    }

    @Test
    void rejectsUnsupportedAndroidProtocolAsAppProtocolUnsupported() {
        ClientCompatibilityResult result = ClientCompatibilityValidator.validate(androidJoin(2), BUILD_ID);

        assertFalse(result.accepted());
        assertEquals("This app version is not compatible with this server. Update the app.", result.message());
        assertEquals(ConnectionStates.DisconnectCodes.OUTDATED_CLIENT.getCode(), result.closeCode());
        assertEquals("app_protocol_unsupported", result.closeReason());
    }

    @Test
    void rejectsMissingOrBlankClientKindAsInvalidClientInfo() {
        assertInvalidClientInfo(ClientCompatibilityValidator.validate(
                androidJoin(1).put("client", new JSONObject()
                        .put("version", "1.0.0")
                        .put("protocol", 1)),
                BUILD_ID
        ));

        assertInvalidClientInfo(ClientCompatibilityValidator.validate(
                androidJoin(1).put("client", new JSONObject()
                        .put("kind", "   ")
                        .put("version", "1.0.0")
                        .put("protocol", 1)),
                BUILD_ID
        ));
    }

    @Test
    void rejectsUnknownClientKindAsUnsupportedClientType() {
        JSONObject join = androidJoin(1).put("client", new JSONObject()
                .put("kind", "ios")
                .put("version", "1.0.0")
                .put("protocol", 1));

        ClientCompatibilityResult result = ClientCompatibilityValidator.validate(join, BUILD_ID);

        assertFalse(result.accepted());
        assertEquals("Unsupported client type.", result.message());
        assertEquals(ConnectionStates.DisconnectCodes.PACKET_ERROR.getCode(), result.closeCode());
        assertEquals("unsupported_client_type", result.closeReason());
    }

    @Test
    void trimsAndroidVersionForDiagnostics() {
        JSONObject join = androidJoin(1).put("client", new JSONObject()
                .put("kind", "android")
                .put("version", "  1.0.0  ")
                .put("protocol", 1));

        ClientCompatibilityResult result = ClientCompatibilityValidator.validate(join, BUILD_ID);

        assertTrue(result.accepted());
        assertEquals(new ClientIdentity("android", "1.0.0", 1), result.identity());
    }

    private static JSONObject androidJoin(int protocol) {
        return new JSONObject()
                .put("type", "join")
                .put("username", "PlayerName")
                .put("password", "secret")
                .put("client", new JSONObject()
                        .put("kind", "android")
                        .put("version", "1.0.0")
                        .put("protocol", protocol));
    }

    private static void assertInvalidClientInfo(ClientCompatibilityResult result) {
        assertFalse(result.accepted());
        assertEquals("Invalid Android client information.", result.message());
        assertEquals(ConnectionStates.DisconnectCodes.PACKET_ERROR.getCode(), result.closeCode());
        assertEquals("invalid_client_info", result.closeReason());
    }
}
