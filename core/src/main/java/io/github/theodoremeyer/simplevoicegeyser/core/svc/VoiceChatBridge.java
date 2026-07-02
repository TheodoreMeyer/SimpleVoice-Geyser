package io.github.theodoremeyer.simplevoicegeyser.core.svc;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.RemoveGroupEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.packets.EntitySoundPacket;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.VoicePacketForwarder;

import java.util.UUID;

/**
 * The Class Bridging with Simple Voice Chat
 */
public class VoiceChatBridge implements VoicechatPlugin {

    private VoicechatServerApi serverApi;
    private long microphonePacketCount = 0;
    private VoicePacketForwarder.Result microphoneForwardingTotals =
            new VoicePacketForwarder.Result(0, 0, 0, 0, 0, 0);
    private long skippedMalformedMicrophonePackets = 0;

    /**
     * No arg constructor
     */
    public VoiceChatBridge() {}

    @Override
    public String getPluginId() {
        return "SimpleVoice-Geyser";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        SvgCore.getLogger().warning("[VCBridge] Registering events...");
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onVoicechatStarted);
        registration.registerEvent(CreateGroupEvent.class, this::onGroupCreated);
        registration.registerEvent(RemoveGroupEvent.class, this::onGroupRemoved);
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
        SvgCore.getLogger().info("[VCBridge] Java-to-websocket voice forwarding registered.");
    }

    @Override
    public void initialize(VoicechatApi api) {
        SvgCore.getLogger().info("[VCBridge] VoiceChat API initialized");
    }

    private void onVoicechatStarted(VoicechatServerStartedEvent event) {
        serverApi = event.getVoicechat();
        SvgCore.getLogger().info("[VCBridge] Voice chat server started: " + serverApi);
        for (Group group : serverApi.getGroups()) {
            SvgCore.getGroupManager().addGroup(group);
            SvgCore.getLogger().info("[VCBridge] Loaded group: " + group.getName());
        }
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        microphonePacketCount++;

        VoicechatServerApi api = serverApi;
        VoicechatConnection senderConnection = event == null ? null : event.getSenderConnection();
        UUID senderUuid = senderConnection != null && senderConnection.getPlayer() != null
                ? senderConnection.getPlayer().getUuid()
                : null;

        MicrophonePacket microphonePacket = event == null ? null : event.getPacket();
        EntitySoundPacket soundPacket = toEntitySoundPacket(api, senderUuid, microphonePacket);
        if (senderUuid == null || soundPacket == null) {
            skippedMalformedMicrophonePackets++;
            logMicrophoneForwardingStatsIfNeeded();
            return;
        }

        VoicePacketForwarder.Result result =
                SvgCore.getConnectionManager().forwardVoicePacket(senderUuid, soundPacket);
        microphoneForwardingTotals = microphoneForwardingTotals.plus(result);
        logMicrophoneForwardingStatsIfNeeded();
    }

    private EntitySoundPacket toEntitySoundPacket(VoicechatServerApi api, UUID senderUuid, MicrophonePacket packet) {
        if (api == null || senderUuid == null || packet == null) {
            return null;
        }

        byte[] opusData = packet.getOpusEncodedData();
        if (opusData == null || opusData.length == 0) {
            return null;
        }

        try {
            return packet.entitySoundPacketBuilder()
                    .channelId(senderUuid)
                    .entityUuid(senderUuid)
                    .whispering(packet.isWhispering())
                    .distance((float) api.getVoiceChatDistance())
                    .opusEncodedData(opusData)
                    .build();
        } catch (Exception e) {
            SvgCore.getLogger().debug("VoiceChatBridge: Failed to convert microphone packet for websocket forwarding", e);
            return null;
        }
    }

    private void logMicrophoneForwardingStatsIfNeeded() {
        if (microphonePacketCount % 200 != 0) {
            return;
        }

        SvgCore.getLogger().debug(
                "VoiceChatBridge: microphone forwarding stats packets=" + microphonePacketCount
                        + " forwarded=" + microphoneForwardingTotals.forwarded()
                        + " skippedSelf=" + microphoneForwardingTotals.skippedSelf()
                        + " skippedNullSender=" + microphoneForwardingTotals.skippedNullSender()
                        + " skippedInactive=" + microphoneForwardingTotals.skippedInactive()
                        + " skippedMissingListener=" + microphoneForwardingTotals.skippedMissingListener()
                        + " failed=" + microphoneForwardingTotals.failed()
                        + " skippedMalformed=" + skippedMalformedMicrophonePackets
        );
    }

    private void onGroupCreated(CreateGroupEvent event) {
        Group group = event.getGroup();
        if (group != null) {
            SvgCore.getGroupManager().addGroup(group);
        }
    }

    private void onGroupRemoved(RemoveGroupEvent event) {
        Group group = event.getGroup();
        if (group != null) {
            SvgCore.getGroupManager().removeGroup(group);
        }
    }

    /**
     * Get the Server API of SVC
     * @return the server API, or null if the server hasn't started yet.
     */
    public VoicechatServerApi getVcServerApi() {
        return serverApi;
    }
}
