package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiosender.AudioSender;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    /**
     * Single-threaded, bounded uplink queue. Both the PCM and raw-Opus paths run through it
     * so packet order is preserved and the websocket IO thread never blocks on encoding/sending.
     */
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
    /** Set before draining the uplink queue so encode/deliver stop using a closing encoder. */
    private final AtomicBoolean closed = new AtomicBoolean(false);

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
     * Queues PCM16LE microphone audio to be Opus-encoded and sent to the player.
     * Used for clients that cannot encode Opus themselves (see {@link MicAudioFormat#PCM16LE}).
     * @param pcmData PCM16LE data to send
     */
    public void sendPcm16le(byte[] pcmData) {
        if (pcmData == null || closed.get()) {
            return;
        }

        SvgCore.getLogger().debug("AudioSender: received audio data from websocket!");

        byte[] frame = pcmData.clone();
        enqueue(() -> encodeAndSend(frame));
    }

    /**
     * Queues an already validated raw Opus packet to be sent directly to Simple Voice Chat.
     * Used for clients that encode Opus themselves (see {@link MicAudioFormat#OPUS}).
     * @param opusData raw Opus packet data
     */
    public void sendEncodedOpus(byte[] opusData) {
        if (opusData == null || opusData.length == 0 || closed.get()) {
            return;
        }

        byte[] packet = opusData.clone();
        enqueue(() -> deliver(packet));
    }

    /**
     * Hand a task to the uplink thread, dropping it if the queue is full.
     * @param task task to run
     */
    private void enqueue(Runnable task) {
        if (closed.get()) {
            return;
        }
        try {
            audioExecutor.execute(task);
        } catch (RejectedExecutionException e) {
            SvgCore.getLogger().debug("[AudioSender] Dropping queued audio frame for " + playerUuid);
        }
    }

    private void encodeAndSend(byte[] pcmData) {
        if (closed.get()) {
            return;
        }
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
            SvgCore.getLogger().debug("AudioSender: Encoding failed for " + playerUuid, e);
            return;
        }

        deliver(encoded);
    }

    private void deliver(byte[] opusPacket) {
        if (closed.get()) {
            return;
        }
        try {
            if (!delegate.send(opusPacket)) {
                SvgCore.getLogger().warning("[AudioSender] Failed to send audio for: " + playerUuid);
            }
        } catch (Exception e) {
            SvgCore.getLogger().debug("[AudioSender] Failed to deliver audio for " + playerUuid, e);
        }
    }

    /**
     * Closes the AudioSender, and ends decoder.
     * Marks closed first, drains the uplink worker, then closes the Opus encoder
     * so encode cannot race with encoder.close().
     */
    public void unregister() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        audioExecutor.shutdown();
        try {
            if (!audioExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                audioExecutor.shutdownNow();
                audioExecutor.awaitTermination(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            audioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

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
