package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiosender.AudioSender;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;

import java.util.UUID;

/**
 * Audio Sender to send Clients Audio to server
 */
public final class SvgAudioSender {
    private static final int TX_CODEC_PCM16 = 0;
    private static final int TX_CODEC_OPUS = 1;

    /**
     * The simple voice chat api
     */
    private final VoicechatServerApi serverApi;
    /**
     * The player's uuid that the audio sender is associated with
     */
    private final UUID playerUuid;
    /**
     * The audio sender itself
     */
    private final AudioSender delegate;
    /**
     * Audio Encoder to encode the audio for svc
     */
    private final OpusEncoder encoder;

    /**
     * Class Constructor. Creates and registers the audio sender
     * @param serverApi voice chat server api
     * @param playerUuid uuid of player registering sender for.
     */
    public SvgAudioSender(VoicechatServerApi serverApi, UUID playerUuid) {
        this.serverApi = serverApi;
        this.playerUuid = playerUuid;
        this.encoder = serverApi.createEncoder();

        VoicechatConnection connection = serverApi.getConnectionOf(playerUuid);

        if (connection == null) {
            throw new RuntimeException("no svc connection for uuid: " + playerUuid);
        }

        this.delegate = serverApi.createAudioSender(connection); //create the sender itself
        boolean success = serverApi.registerAudioSender(delegate);

        if (success) {
            connection.setConnected(true);
            connection.setDisabled(false);
        } else {
            SvgCore.getLogger().info("Failed to register SvgAudioSender for UUID: " + playerUuid);
        }
    }

    /**
     * Sends a client audio packet to SVC, supporting both marked Opus and legacy/raw PCM payloads.
     *
     * @param packetData client packet payload (optional 1-byte codec marker + audio payload)
     */
    public void sendOpus(byte[] packetData) {
        SvgCore.getLogger().debug("AudioSender: received audio data from websocket!");

        if (packetData == null || packetData.length == 0) {
            return;
        }

        int codecMarker = packetData[0] & 0xFF;
        byte[] payload = packetData;
        if (codecMarker == TX_CODEC_PCM16 || codecMarker == TX_CODEC_OPUS) {
            payload = new byte[packetData.length - 1];
            System.arraycopy(packetData, 1, payload, 0, payload.length);
        } else {
            // Backward compatibility with older web clients that send raw PCM without marker.
            codecMarker = TX_CODEC_PCM16;
        }

        if (codecMarker == TX_CODEC_OPUS) {
            boolean success = delegate.send(payload);
            if (!success) {
                SvgCore.getLogger().warning("[AudioSender] Failed to send opus payload for: " + playerUuid);
            }
            return;
        }

        byte[] encoded;
        try {
            short[] pcmShorts = serverApi.getAudioConverter().bytesToShorts(payload);
            if (pcmShorts.length != 960) {
                SvgCore.getLogger().warning(
                        "[AudioSender] Invalid frame size: " + pcmShorts.length + " (expected 960)"
                );
                return;
            }

            encoded = encoder.encode(pcmShorts); // PCM to Opus encoded
            if (encoded == null || encoded.length == 0) {
                SvgCore.getLogger().warning("[AudioSender] Encoder returned empty data for: " + playerUuid);
                return;
            }
        } catch (Exception e) {
            SvgCore.getLogger().debug("AudioSender: Encoding failed for " + playerUuid, e);
            return;
        }

        boolean success = delegate.send(encoded);
        if (!success) {
            SvgCore.getLogger().warning("[AudioSender] Failed to send audio for: " + playerUuid);
        }
    }

    /**
     * Closes the AudioSender, and ends decoder
     */
    public void unregister() {
        try {
            if (encoder != null) {
                encoder.resetState();
                encoder.close();
            }
        } catch (Exception e) {
            SvgCore.getLogger().warning("[SvgAudioSender] Failed to close encoder for " + playerUuid + ": " + e.getMessage());
        }
        delegate.reset();
        serverApi.unregisterAudioSender(delegate); //end sender
    }
}
