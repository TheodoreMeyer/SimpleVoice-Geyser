package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioSessionNegotiation;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioTransportMode;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioTransportPreference;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public final class CapabilitiesPacket implements Packet {

    @Override
    public String getType() {
        return "capabilities";
    }

    @Override
    public void handle(JettyWebSocket socket, JSONObject json) {

        long count = socket.addCapabilityMessage();

        JSONObject audio = json.optJSONObject("audio");
        if (audio == null) {
            socket.sendRaw(
                    ConnectionStates.MessageType.ERROR,
                    "Invalid capabilities payload.",
                    false
            );
            return;
        }

        JSONArray protocols = audio.optJSONArray("protocols");

        boolean supportsLegacy = true;
        boolean supportsSvgV2 = false;

        if (protocols != null) {
            supportsLegacy = false;

            for (int i = 0; i < protocols.length(); i++) {
                String protocol = String.valueOf(protocols.opt(i))
                        .trim()
                        .toLowerCase(Locale.ROOT);

                switch (protocol) {
                    case "legacy" -> supportsLegacy = true;
                    case "svg-v2", "svg_v2", "v2" -> supportsSvgV2 = true;
                }
            }
        }

        JSONObject decoder = audio.optJSONObject("decoder");

        boolean wasm =
                decoder != null && decoder.optBoolean("opusWasm", false);

        boolean webCodecs =
                decoder != null && decoder.optBoolean("webCodecs", false);

        boolean supportsOpusDecoder =
                wasm || webCodecs || audio.optBoolean("supportsOpusDecoder", false);

        boolean secureContext =
                audio.optBoolean("secureContext", false);

        AudioSessionNegotiation negotiation = socket.getAudioNegotiation();

        if (negotiation == null) {
            negotiation = new AudioSessionNegotiation(
                    AudioTransportPreference.fromConfig(
                            SvgCore.getConfig().AUDIO_TRANSPORT_MODE.get()
                    ),
                    Boolean.TRUE.equals(
                            SvgCore.getConfig().AUDIO_ALLOW_LEGACY_FALLBACK.get()
                    )
            );

            socket.setAudioNegotiation(negotiation);
        }

        negotiation.updateClientCapabilities(
                supportsLegacy,
                supportsSvgV2,
                supportsOpusDecoder,
                secureContext
        );

        AudioTransportMode selected = negotiation.getSelectedMode();

        JSONObject ack = new JSONObject();
        ack.put("type", "capabilities_ack");
        ack.put("selectedMode",
                selected == AudioTransportMode.SVG_V2
                        ? "svg-v2"
                        : "legacy");
        ack.put("fallbackCount", negotiation.getFallbackCount());

        if (!socket.sendJson(ack)) {
            SvgCore.getLogger().debug(
                    "WebSocket: Failed to send capabilities ack"
            );
        }

        SvgCore.getLogger().debug(
                "WebSocket: Capabilities #" + count
                        + " uuid="
                        + (socket.getConnection() == null
                        ? "pending"
                        : socket.getConnection().getUuid())
                        + " legacy=" + supportsLegacy
                        + " svgV2=" + supportsSvgV2
                        + " opusDecoder=" + supportsOpusDecoder
                        + " secure=" + secureContext
                        + " selected="
                        + selected.name().toLowerCase(Locale.ROOT)
        );
    }
}