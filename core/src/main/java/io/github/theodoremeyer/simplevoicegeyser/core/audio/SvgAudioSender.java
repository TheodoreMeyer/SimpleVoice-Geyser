package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiosender.AudioSender;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Audio Sender to send Clients Audio to server.
 * <p>
 * Jetty websocket callbacks must only submit frames; Opus encode runs on
 * {@link SessionAudioPipeline} via {@link AudioPipelineExecutor}.
 * Outbound frames are fail-closed behind {@link VoiceTransmitGate}.
 */
public final class SvgAudioSender {

    private final VoicechatServerApi serverApi;
    private final UUID playerUuid;
    private final AudioSender delegate;
    private final SessionAudioPipeline pipeline;
    private final VoiceTransmitGate transmitGate;
    private final AtomicLong legacySequence = new AtomicLong();
    private final AtomicBoolean unregistered = new AtomicBoolean(false);

    /**
     * Class Constructor. Creates and registers the audio sender
     * @param serverApi voice chat server api
     * @param playerUuid uuid of player registering sender for.
     * @param sessionGeneration authenticated session generation
     */
    public SvgAudioSender(VoicechatServerApi serverApi, UUID playerUuid, long sessionGeneration) {
        this.serverApi = serverApi;
        this.playerUuid = playerUuid;
        this.transmitGate = new VoiceTransmitGate(playerUuid, sessionGeneration);

        OpusEncoder opusEncoder = serverApi.createEncoder();

        VoicechatConnection connection = serverApi.getConnectionOf(playerUuid);

        if (connection == null) {
            try {
                opusEncoder.close();
            } catch (Exception ignored) {
            }
            throw new RuntimeException("no svc connection for uuid: " + playerUuid);
        }

        this.delegate = serverApi.createAudioSender(connection);
        boolean success = serverApi.registerAudioSender(delegate);

        if (success) {
            connection.setConnected(true);
            connection.setDisabled(false);
        } else {
            SvgCore.getLogger().info("Failed to register SvgAudioSender for UUID: " + playerUuid);
        }

        int capacity = AudioFormatConstants.QUEUE_HARD_CAPACITY;
        try {
            capacity = SessionAudioPipeline.clampQueueCapacity(
                    SvgCore.getConfig().AUDIO_OUTBOUND_QUEUE_CAPACITY.get()
            );
        } catch (RuntimeException ignored) {
            // SvgCore may be unavailable in isolated construction; keep default.
        }

        this.pipeline = new SessionAudioPipeline(
                playerUuid,
                new OpusEncoderAdapter(opusEncoder),
                new SessionAudioPipeline.EncodedSink() {
                    @Override
                    public boolean send(byte[] opus) {
                        return delegate.send(opus);
                    }

                    @Override
                    public void endStream() {
                        try {
                            delegate.reset();
                        } catch (RuntimeException ex) {
                            SvgCore.getLogger().debug(
                                    "SvgAudioSender: AudioSender.reset failed for " + playerUuid,
                                    ex
                            );
                        }
                    }
                },
                capacity,
                AudioPipelineExecutor.runner(),
                transmitGate
        );
    }

    /**
     * Package-private constructor for tests.
     */
    SvgAudioSender(
            VoicechatServerApi serverApi,
            UUID playerUuid,
            AudioSender delegate,
            SessionAudioPipeline pipeline,
            VoiceTransmitGate transmitGate
    ) {
        this.serverApi = serverApi;
        this.playerUuid = playerUuid;
        this.delegate = delegate;
        this.pipeline = pipeline;
        this.transmitGate = transmitGate;
    }

    /**
     * Decode a websocket binary payload and submit it to the session pipeline.
     * Does not encode on the calling (Jetty) thread.
     *
     * @param data PCM legacy or SVTX-framed mic payload
     */
    public void sendOpus(byte[] data) {
        if (!transmitGate.allowsTransmit()) {
            transmitGate.onDroppedFrame();
            if (pipeline != null) {
                pipeline.getDiagnostics().onPrivacyDrop();
            }
            return;
        }
        Optional<MicFrame> frame = MicFrameCodec.decode(data, System.nanoTime(), legacySequence);
        if (frame.isEmpty()) {
            SvgCore.getLogger().debug(
                    "AudioSender: dropping unrecognized mic payload bytes="
                            + (data == null ? -1 : data.length)
                            + " uuid=" + playerUuid
            );
            return;
        }
        sendFrame(frame.get());
    }

    /**
     * Submit an already-decoded microphone frame.
     *
     * @param frame validated mic frame
     */
    public void sendFrame(MicFrame frame) {
        if (frame == null) {
            return;
        }
        if (!transmitGate.allowsTransmit()) {
            transmitGate.onDroppedFrame();
            pipeline.getDiagnostics().onPrivacyDrop();
            return;
        }
        pipeline.submit(frame);
    }

    /**
     * @return outbound privacy gate
     */
    public VoiceTransmitGate getTransmitGate() {
        return transmitGate;
    }

    /**
     * Apply authoritative membership. Stale session generations are ignored.
     *
     * @param membership membership snapshot
     * @return true when applied
     */
    public boolean applyMembership(SessionVoiceMembership membership) {
        return transmitGate.applyMembership(membership);
    }

    /**
     * Close the transmit gate immediately and clear queued frames.
     *
     * @param membershipRevision revision
     */
    public void closeTransmit(long membershipRevision) {
        transmitGate.closeTransmit(membershipRevision);
    }

    /**
     * Closes the pipeline/encoder exactly once and unregisters the SVC audio sender.
     */
    public void unregister() {
        if (!unregistered.compareAndSet(false, true)) {
            return;
        }

        try {
            transmitGate.closeTransmit(transmitGate.getMembership().membershipRevision());
        } catch (Exception ignored) {
        }

        try {
            pipeline.close();
        } catch (Exception e) {
            SvgCore.getLogger().warning(
                    "[SvgAudioSender] Failed to close pipeline for " + playerUuid + ": " + e.getMessage()
            );
        }

        try {
            delegate.reset();
        } catch (Exception e) {
            SvgCore.getLogger().debug("SvgAudioSender: reset failed for " + playerUuid, e);
        }

        try {
            serverApi.unregisterAudioSender(delegate);
        } catch (Exception e) {
            SvgCore.getLogger().debug("SvgAudioSender: unregister failed for " + playerUuid, e);
        }
    }

    /**
     * @return session pipeline (tests)
     */
    SessionAudioPipeline getPipeline() {
        return pipeline;
    }

    /**
     * Adapts SVC {@link OpusEncoder} to {@link SessionAudioPipeline.Encoder}.
     */
    static final class OpusEncoderAdapter implements SessionAudioPipeline.Encoder {
        private final OpusEncoder encoder;

        OpusEncoderAdapter(OpusEncoder encoder) {
            this.encoder = encoder;
        }

        @Override
        public byte[] encode(short[] pcm) {
            return encoder.encode(pcm);
        }

        @Override
        public void resetState() {
            encoder.resetState();
        }

        @Override
        public void close() {
            encoder.close();
        }
    }
}
