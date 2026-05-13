package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiolistener.PlayerAudioListener;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.packets.EntitySoundPacket;
import de.maxhenkel.voicechat.api.packets.LocationalSoundPacket;
import de.maxhenkel.voicechat.api.packets.SoundPacket;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import org.eclipse.jetty.websocket.api.Session;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Class Sending Audio to Client
 */
public final class SvgAudioListener implements PlayerAudioListener {

    private final UUID listenerId;
    private final VoicechatServerApi serverApi;
    private final OpusDecoder decoder;
    private final Object decoderLock = new Object();
    private final AtomicBoolean closing = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private long packetReceivedCount = 0;
    private long packetDecodeFailedCount = 0;
    private long packetSentCount = 0;
    private long packetSendFailedCount = 0;
    private long packetSentByteCount = 0;
    private long packetSentStereoSampleCount = 0;
    private long packetMalformedStereoFrameCount = 0;
    private long packetClosedDropCount = 0;
    private long attenuationAppliedCount = 0;
    private long attenuationBypassIneligibleGroupCount = 0;
    private long attenuationBypassNoSourceCount = 0;
    private long attenuationBypassNoDistanceCount = 0;
    private long pannedCount = 0;
    private long panBypassNoYawCount = 0;
    private long panBypassNoSourceCount = 0;
    private long decodedSampleCount = 0;
    private long packetSentLegacyCount = 0;
    private long packetSentSvgV2Count = 0;

    /**
     * Session, used for less method checks
     */
    private final Session session;
    private final AudioSessionNegotiation negotiation;
    private final AudioByteCompiler audioByteCompiler;

    /**
     * Class constructor to set id
     * @param listenerId the id of this listener
     * @param session the Session to link to
     * @param serverApi the VcServer API
     */
    public SvgAudioListener(UUID listenerId, Session session, VoicechatServerApi serverApi, AudioSessionNegotiation negotiation) {
        this.listenerId = listenerId;
        this.session = session;
        this.serverApi = serverApi;
        this.negotiation = negotiation;
        this.audioByteCompiler = SvgCore.getAudioByteCompiler();

        // Decoder for opus to raw PCM (16-bit signed, little-endian)
        decoder = serverApi.createDecoder();
    }

    /**
     * Get the uuid of this listener
     * The id of the listener is currently the id of the player it is listening for
     * @return UUID of the listener
     */
    @Override
    public UUID getListenerId() {
        return listenerId;
    }

    /**
     * Called when audio data is received for the player.
     * This method forwards audio to the player's WebSocket session.
     * @param soundPacket packet received to send to Client
     */
    public void onAudioReceived(SoundPacket soundPacket) {
        packetReceivedCount++;

        if (closed.get() || closing.get()) {
            packetClosedDropCount++;
            logStatsIfNeeded();
            return;
        }

        if (!session.isOpen()) {
            packetClosedDropCount++;
            SvgCore.debug("AudioListener", "Dropping packet while websocket not open for " + listenerId);
            logStatsIfNeeded();
            return;
        }

        byte[] opusData = soundPacket.getOpusEncodedData();
        AudioThread.execute(() -> {
            if (closed.get() || closing.get()) {
                packetClosedDropCount++;
                return;
            }
            try {
                VoicechatConnection receiverConnection = serverApi.getConnectionOf(listenerId);
                Position receiverPosition = receiverConnection != null && receiverConnection.getPlayer() != null
                        ? receiverConnection.getPlayer().getPosition()
                        : null;
                SpatialContext context = resolveSpatialContext(soundPacket);

                float distanceGain = computeDistanceGain(receiverConnection, receiverPosition, context);
                float pan = computePan(receiverConnection, receiverPosition, context);
                if (Math.abs(pan) > 0.001f) {
                    pannedCount++;
                }

                AudioTransportMode transportMode = negotiation == null
                        ? AudioTransportMode.LEGACY
                        : negotiation.getSelectedMode();

                byte[] bytes;
                if (transportMode == AudioTransportMode.SVG_V2) {
                    byte flags = 0;
                    if (context.staticPacket) {
                        flags |= AudioByteCompiler.FLAG_STATIC_PACKET;
                    }
                    if (distanceGain < 0.999f) {
                        flags |= AudioByteCompiler.FLAG_DISTANCE_ATTENUATED;
                        attenuationAppliedCount++;
                    }
                    if (Math.abs(pan) > 0.001f) {
                        flags |= AudioByteCompiler.FLAG_HAS_PAN;
                    }
                    bytes = audioByteCompiler.compileSvgV2Opus(
                            readSequenceNumber(soundPacket),
                            pan,
                            distanceGain,
                            flags,
                            opusData
                    );
                    packetSentSvgV2Count++;
                } else {
                    short[] pcm;
                    synchronized (decoderLock) {
                        if (closed.get() || closing.get()) {
                            packetClosedDropCount++;
                            return;
                        }
                        pcm = decoder.decode(opusData);
                    }
                    if (pcm == null || pcm.length == 0) {
                        packetDecodeFailedCount++;
                        SvgCore.debug("AudioListener", "Decoded empty PCM packet for " + listenerId);
                        return;
                    }
                    decodedSampleCount += pcm.length;

                    if (distanceGain < 0.999f) {
                        applyGain(pcm, distanceGain);
                        attenuationAppliedCount++;
                    }
                    short[] stereoPcm = toStereoPcm(pcm, pan);
                    bytes = audioByteCompiler.compileLegacyStereoPcm(stereoPcm);
                    packetSentStereoSampleCount += stereoPcm.length;
                    packetSentLegacyCount++;
                    if (bytes.length % 4 != 0) {
                        packetMalformedStereoFrameCount++;
                    }
                }

                if (!session.isOpen()) {
                    packetClosedDropCount++;
                    return;
                }

                packetSentByteCount += bytes.length;
                session.getRemote().sendBytes(ByteBuffer.wrap(bytes));
                packetSentCount++;
            } catch (Exception e) {
                packetSendFailedCount++;
                SvgCore.debug("AudioListener", "Error sending audio to client " + listenerId, e);
            } finally {
                logStatsIfNeeded();
            }
        });
    }

    private void logStatsIfNeeded() {
        if (packetReceivedCount % 200 != 0) {
            return;
        }
        SvgCore.debug("AudioListener",
                "stats uuid=" + listenerId
                        + " recv=" + packetReceivedCount
                        + " decodeFail=" + packetDecodeFailedCount
                        + " sent=" + packetSentCount
                        + " sendFail=" + packetSendFailedCount
                        + " sentBytes=" + packetSentByteCount
                        + " malformedStereo=" + packetMalformedStereoFrameCount
                        + " droppedClosed=" + packetClosedDropCount
                        + " attenuated=" + attenuationAppliedCount
                        + " bypassGroup=" + attenuationBypassIneligibleGroupCount
                        + " bypassNoSource=" + attenuationBypassNoSourceCount
                        + " bypassNoDistance=" + attenuationBypassNoDistanceCount
                        + " panned=" + pannedCount
                        + " panBypassNoYaw=" + panBypassNoYawCount
                        + " panBypassNoSource=" + panBypassNoSourceCount
                        + " sentLegacy=" + packetSentLegacyCount
                        + " sentSvgV2=" + packetSentSvgV2Count
                        + " fallbackCount=" + (negotiation == null ? 0 : negotiation.getFallbackCount())
                        + " avgWireBytes=" + (packetSentCount == 0 ? 0 : (packetSentByteCount / packetSentCount))
                        + " avgStereoSamples=" + (packetSentCount == 0 ? 0 : (packetSentStereoSampleCount / packetSentCount))
                        + " avgSamples=" + (packetSentCount == 0 ? 0 : (decodedSampleCount / packetSentCount)));
    }

    private float computeDistanceGain(VoicechatConnection receiverConnection, Position receiverPosition, SpatialContext context) {
        if (context.staticPacket) {
            return 1.0f;
        }

        if (isDistanceAttenuationIneligible(receiverConnection)) {
            attenuationBypassIneligibleGroupCount++;
            return 1.0f;
        }

        if (receiverPosition == null) {
            attenuationBypassNoSourceCount++;
            return 1.0f;
        }
        Position sourcePosition = context.sourcePosition;
        float maxDistance = context.maxDistance;
        String packetType = context.packetType;

        if (sourcePosition == null) {
            attenuationBypassNoSourceCount++;
            return 1.0f;
        }
        if (maxDistance <= 0.0f) {
            attenuationBypassNoDistanceCount++;
            return 1.0f;
        }

        double dx = sourcePosition.getX() - receiverPosition.getX();
        double dy = sourcePosition.getY() - receiverPosition.getY();
        double dz = sourcePosition.getZ() - receiverPosition.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance > maxDistance) {
            if (packetReceivedCount % 200 == 0) {
                SvgCore.debug("AudioListener",
                        "attenuation packet=" + packetType
                                + " distance=" + String.format(Locale.ROOT, "%.2f", distance)
                                + " maxDistance=" + String.format(Locale.ROOT, "%.2f", (double) maxDistance)
                                + " gain=0.000");
            }
            return 0.0f;
        }

        // Smooth fade: full near source, down to 0 at max distance with an edge ramp to avoid abrupt cutoff.
        float normalized = (float) Math.max(0.0D, Math.min(1.0D, distance / maxDistance));
        float edgeStart = 0.97f;
        float gain;
        if (normalized >= edgeStart) {
            float edgeNormalized = (normalized - edgeStart) / (1.0f - edgeStart);
            gain = Math.max(0.0f, 1.0f - edgeNormalized);
        } else {
            gain = (float) Math.pow(Math.cos(normalized * (Math.PI / 2.0D)), 2.0D);
        }
        if (packetReceivedCount % 200 == 0) {
            SvgCore.debug("AudioListener",
                    "attenuation packet=" + packetType
                            + " distance=" + String.format(Locale.ROOT, "%.2f", distance)
                            + " maxDistance=" + String.format(Locale.ROOT, "%.2f", (double) maxDistance)
                            + " gain=" + String.format(Locale.ROOT, "%.3f", gain));
        }
        return gain;
    }

    private float computePan(VoicechatConnection receiverConnection, Position receiverPosition, SpatialContext context) {
        if (receiverPosition == null) {
            panBypassNoSourceCount++;
            return 0.0f;
        }
        if (context.staticPacket || context.sourcePosition == null) {
            panBypassNoSourceCount++;
            return 0.0f;
        }
        Double yaw = resolveReceiverYaw(receiverConnection);
        if (yaw == null) {
            panBypassNoYawCount++;
            return 0.0f;
        }

        double dx = context.sourcePosition.getX() - receiverPosition.getX();
        double dz = context.sourcePosition.getZ() - receiverPosition.getZ();
        double horizontalLength = Math.sqrt(dx * dx + dz * dz);
        if (horizontalLength < 1.0E-6D) {
            return 0.0f;
        }

        double toSourceX = dx / horizontalLength;
        double toSourceZ = dz / horizontalLength;

        double yawRad = Math.toRadians(yaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double rightX = -forwardZ;
        double rightZ = forwardX;
        float pan = (float) (toSourceX * rightX + toSourceZ * rightZ);
        pan = Math.max(-1.0f, Math.min(1.0f, pan));

        if (packetReceivedCount % 400 == 0) {
            SvgCore.debug("AudioListener",
                    "pan packet=" + context.packetType
                            + " yaw=" + String.format(Locale.ROOT, "%.2f", yaw)
                            + " pan=" + String.format(Locale.ROOT, "%.3f", pan));
        }
        return pan;
    }

    private boolean isDistanceAttenuationIneligible(VoicechatConnection receiverConnection) {
        if (receiverConnection == null) {
            return false;
        }

        if (!receiverConnection.isInGroup() || receiverConnection.getGroup() == null) {
            return false;
        }

        Group receiverGroup = receiverConnection.getGroup();
        if (receiverGroup == null || receiverGroup.getType() == null) {
            return false;
        }

        String type = String.valueOf(receiverGroup.getType()).toUpperCase(Locale.ROOT);
        return "NORMAL".equals(type) || "ISOLATED".equals(type);
    }

    private SpatialContext resolveSpatialContext(SoundPacket soundPacket) {
        boolean staticPacket = soundPacket instanceof StaticSoundPacket;
        String packetType = soundPacket.getClass().getSimpleName();
        Position sourcePosition = null;
        float maxDistance = -1.0f;

        if (soundPacket instanceof LocationalSoundPacket locational) {
            sourcePosition = locational.getPosition();
            maxDistance = locational.getDistance();
        }

        if (sourcePosition == null && soundPacket instanceof EntitySoundPacket entitySound) {
            sourcePosition = getEntityPacketSource(entitySound, soundPacket);
            maxDistance = entitySound.getDistance();
        }

        if (sourcePosition == null) {
            Position senderPosition = getSenderPosition(soundPacket.getSender());
            LocationalSoundPacket convertedLocational = safeToLocational(soundPacket, senderPosition);
            if (convertedLocational != null) {
                sourcePosition = convertedLocational.getPosition();
                if (maxDistance <= 0.0f) {
                    maxDistance = convertedLocational.getDistance();
                }
                packetType = packetType + "->Locational";
            }
        }

        if (sourcePosition == null) {
            EntitySoundPacket convertedEntity = safeToEntity(soundPacket, soundPacket.getSender());
            if (convertedEntity != null) {
                sourcePosition = getEntityPacketSource(convertedEntity, soundPacket);
                if (maxDistance <= 0.0f) {
                    maxDistance = convertedEntity.getDistance();
                }
                packetType = packetType + "->Entity";
            }
        }

        if (sourcePosition == null) {
            sourcePosition = getSenderPosition(soundPacket.getSender());
            if (sourcePosition != null) {
                packetType = packetType + "->Sender";
            }
        }

        if (maxDistance <= 0.0f) {
            maxDistance = readDistanceReflective(soundPacket);
        }

        if (maxDistance <= 0.0f) {
            maxDistance = (float) serverApi.getVoiceChatDistance();
        }

        return new SpatialContext(sourcePosition, maxDistance, packetType, staticPacket);
    }

    private Position getEntityPacketSource(EntitySoundPacket entitySound, SoundPacket originalPacket) {
        VoicechatConnection senderConnection = serverApi.getConnectionOf(entitySound.getEntityUuid());
        if (senderConnection != null && senderConnection.getPlayer() != null) {
            return senderConnection.getPlayer().getPosition();
        }

        VoicechatConnection fallbackConnection = serverApi.getConnectionOf(originalPacket.getSender());
        if (fallbackConnection != null && fallbackConnection.getPlayer() != null) {
            return fallbackConnection.getPlayer().getPosition();
        }

        return null;
    }

    private Position getSenderPosition(UUID senderUuid) {
        VoicechatConnection senderConnection = serverApi.getConnectionOf(senderUuid);
        if (senderConnection == null || senderConnection.getPlayer() == null) {
            return null;
        }
        return senderConnection.getPlayer().getPosition();
    }

    private LocationalSoundPacket safeToLocational(SoundPacket soundPacket, Position sourcePosition) {
        if (sourcePosition == null) {
            return null;
        }
        try {
            return soundPacket.toLocationalSoundPacket(sourcePosition);
        } catch (Exception ignored) {
            return null;
        }
    }

    private EntitySoundPacket safeToEntity(SoundPacket soundPacket, UUID entityUuid) {
        if (entityUuid == null) {
            return null;
        }
        try {
            return soundPacket.toEntitySoundPacket(entityUuid, false);
        } catch (Exception ignored) {
            return null;
        }
    }

    private float readDistanceReflective(SoundPacket soundPacket) {
        try {
            Object value = soundPacket.getClass().getMethod("getDistance").invoke(soundPacket);
            if (value instanceof Number number) {
                return number.floatValue();
            }
        } catch (Exception ignored) {
        }
        return -1.0f;
    }

    private long readSequenceNumber(SoundPacket soundPacket) {
        try {
            Object value = soundPacket.getClass().getMethod("getSequenceNumber").invoke(soundPacket);
            if (value instanceof Number number) {
                return number.longValue();
            }
        } catch (Exception ignored) {
        }
        try {
            Object value = soundPacket.getClass().getMethod("getSequence").invoke(soundPacket);
            if (value instanceof Number number) {
                return number.longValue();
            }
        } catch (Exception ignored) {
        }
        return packetReceivedCount;
    }

    private Double resolveReceiverYaw(VoicechatConnection receiverConnection) {
        if (receiverConnection == null || receiverConnection.getPlayer() == null) {
            return null;
        }
        Object platformPlayer = receiverConnection.getPlayer().getPlayer();
        if (platformPlayer == null) {
            return null;
        }

        // Bukkit: Player#getLocation().getYaw()
        try {
            Method getLocation = platformPlayer.getClass().getMethod("getLocation");
            Object location = getLocation.invoke(platformPlayer);
            if (location != null) {
                Double yaw = invokeNumericNoArgs(location, "getYaw");
                if (yaw != null) {
                    return yaw;
                }
            }
        } catch (Exception ignored) {
        }

        // Fabric/NMS fallback
        Double yaw = invokeNumericNoArgs(platformPlayer, "getYRot");
        if (yaw != null) {
            return yaw;
        }
        return invokeNumericNoArgs(platformPlayer, "getYHeadRot");
    }

    private Double invokeNumericNoArgs(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private short[] toStereoPcm(short[] monoPcm, float pan) {
        if (monoPcm == null || monoPcm.length == 0) {
            return new short[0];
        }

        float clampedPan = Math.max(-1.0f, Math.min(1.0f, pan));
        double panTheta = (clampedPan + 1.0D) * (Math.PI / 4.0D);
        float leftGain = (float) Math.cos(panTheta);
        float rightGain = (float) Math.sin(panTheta);

        short[] stereo = new short[monoPcm.length * 2];
        int outIndex = 0;
        for (short sample : monoPcm) {
            int left = Math.round(sample * leftGain);
            int right = Math.round(sample * rightGain);
            if (left > Short.MAX_VALUE) left = Short.MAX_VALUE;
            if (left < Short.MIN_VALUE) left = Short.MIN_VALUE;
            if (right > Short.MAX_VALUE) right = Short.MAX_VALUE;
            if (right < Short.MIN_VALUE) right = Short.MIN_VALUE;
            stereo[outIndex++] = (short) left;
            stereo[outIndex++] = (short) right;
        }
        return stereo;
    }

    private static final class SpatialContext {
        private final Position sourcePosition;
        private final float maxDistance;
        private final String packetType;
        private final boolean staticPacket;

        private SpatialContext(Position sourcePosition, float maxDistance, String packetType, boolean staticPacket) {
            this.sourcePosition = sourcePosition;
            this.maxDistance = maxDistance;
            this.packetType = packetType;
            this.staticPacket = staticPacket;
        }
    }

    private void applyGain(short[] pcm, float gain) {
        if (gain >= 0.999f) {
            return;
        }
        if (gain <= 0.0f) {
            for (int i = 0; i < pcm.length; i++) {
                pcm[i] = 0;
            }
            return;
        }
        for (int i = 0; i < pcm.length; i++) {
            int sample = Math.round(pcm[i] * gain);
            if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE;
            if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE;
            pcm[i] = (short) sample;
        }
    }


    /**
     * Registers the listener with the VoiceChat server.
     * May be moved to class constructor
     */
    public boolean registerListener() {
        PlayerAudioListener listener = serverApi.playerAudioListenerBuilder()
                .setPlayer(listenerId)
                .setPacketListener(this::onAudioReceived)
                .build();

        SvgPlayer player = SvgCore.getPlayerManager().getPlayer(listenerId);
        if (serverApi.registerAudioListener(listener)) { //make sure SVC successfully registered
            SvgCore.getLogger().info("[VCBridge] Registered audio listener for: " + listenerId);
            if (player != null) {
                player.sendMessage(SvgCore.getPrefix() + SvgColor.AQUA + "Registered Audio listener!");
            }
            return true;
        } else {
            SvgCore.getLogger().warning("[VCBridge] Failed to register audio listener for: " + listenerId);
            if (player != null) {
                player.sendMessage(SvgCore.getPrefix() + SvgColor.RED + "Failed to register Audio Listener.");
            }
            return false;
        }
    }

    /**
     * Closes the decoder
     */
    public void unRegister() {
        if (closed.getAndSet(true)) {
            return;
        }
        closing.set(true);
        try {
            synchronized (decoderLock) {
                decoder.resetState();
                decoder.close();
            }
        } catch (Exception e) {
            SvgCore.debug("AudioListener", "Failed closing decoder for " + listenerId, e);
        }
    }

    /**
     * Returns the UUID that the listener is associated with
     * Is the same as listener id
     * @return UUID the player's uuid
     */
    @Override
    public UUID getPlayerUuid() {
        return listenerId;
    }
}
