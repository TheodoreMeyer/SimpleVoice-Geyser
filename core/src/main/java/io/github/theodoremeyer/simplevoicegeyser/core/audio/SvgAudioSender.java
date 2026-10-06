package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiosender.AudioSender;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

/**
 * Audio Sender to send Clients Audio to server
 */
public final class SvgAudioSender {

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
    private final ThreadPoolExecutor audioExecutor = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8),
            runnable -> {
                Thread thread = new Thread(runnable, "SVG-Audio-Uplink");
                thread.setDaemon(true);
                return thread;
            }
    );

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
     * Sends Opus-encoded audio to the player if conditions are met.
     * @param pcmData data to send for player
     */
    public void sendOpus(byte[] pcmData) {
        if (pcmData == null) {
            return;
        }

        byte[] frame = pcmData.clone();
        try {
            audioExecutor.execute(() -> encodeAndSend(frame));
        } catch (RejectedExecutionException e) {
            SvgCore.getLogger().debug("[AudioSender] Dropping queued audio frame for " + playerUuid);
        }
    }

    private void encodeAndSend(byte[] pcmData) {
        byte[] encoded;
        try {
            short[] pcmShorts = serverApi.getAudioConverter().bytesToShorts(pcmData);
            if (pcmShorts.length != 960) {
                SvgCore.getLogger().warning(
                        "[AudioSender] Invalid frame size: " + pcmShorts.length + " (expected 960)"
                );
                return;
            }

            encoded = encoder.encode(pcmShorts);
            if (encoded == null || encoded.length == 0) {
                SvgCore.getLogger().warning("[AudioSender] Encoder returned empty data for: " + playerUuid);
                return;
            }
        } catch (Exception e) {
            SvgCore.getLogger().debug("[AudioSender] Encoding failed for " + playerUuid, e);
            return;
        }

        try {
            if (!delegate.send(encoded)) {
                SvgCore.getLogger().warning("[AudioSender] Failed to send audio for: " + playerUuid);
            }
        } catch (Exception e) {
            SvgCore.getLogger().debug("[AudioSender] Failed to deliver audio for " + playerUuid, e);
        }
    }

    /**
     * Closes the AudioSender, and ends decoder
     */
    public void unregister() {
        audioExecutor.shutdownNow();
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
