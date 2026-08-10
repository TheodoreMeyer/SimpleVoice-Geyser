package io.github.theodoremeyer.simplevoicegeyser.core.svc;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.JoinGroupEvent;
import de.maxhenkel.voicechat.api.events.LeaveGroupEvent;
import de.maxhenkel.voicechat.api.events.RemoveGroupEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.managers.GroupManager;

/**
 * The Class Bridging with Simple Voice Chat
 */
public class VoiceChatBridge implements VoicechatPlugin {

    private VoicechatServerApi serverApi;

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
        registration.registerEvent(JoinGroupEvent.class, this::onGroupJoined);
        registration.registerEvent(LeaveGroupEvent.class, this::onGroupLeft);
    }

    @Override
    public void initialize(VoicechatApi api) {
        SvgCore.getLogger().info("[VCBridge] VoiceChat API initialized");
    }

    private void onVoicechatStarted(VoicechatServerStartedEvent event) {
        serverApi = event.getVoicechat();
        SvgCore.getLogger().info("[VCBridge] Voice chat server started: " + serverApi);
        GroupManager manager = SvgCore.getGroupManager();
        if (manager != null) {
            manager.reconcileFromApi(serverApi.getGroups());
            for (Group group : serverApi.getGroups()) {
                SvgCore.getLogger().info("[VCBridge] Loaded group: " + group.getName());
            }
        }
    }

    private void onGroupCreated(CreateGroupEvent event) {
        GroupManager manager = SvgCore.getGroupManager();
        if (manager == null || serverApi == null) {
            return;
        }

        Group group = event.getGroup();
        String createdId = group == null || group.getId() == null ? null : group.getId().toString();
        String createdName = group == null ? null : group.getName();

        // Explicit authoritative reconcile — never trust a single live Group over the wire.
        // Deduplicates revision bumps when SVG already reconciled after create/join.
        manager.onExternalDirectoryEvent(createdId, createdName, null, group);
    }

    private void onGroupRemoved(RemoveGroupEvent event) {
        GroupManager manager = SvgCore.getGroupManager();
        if (manager == null || serverApi == null) {
            return;
        }

        Group group = event.getGroup();
        String removedId = group == null || group.getId() == null ? null : group.getId().toString();

        // Closes voice gates for members before directory publish.
        manager.onExternalDirectoryEvent(null, null, removedId, null);
    }

    private void onGroupJoined(JoinGroupEvent event) {
        GroupManager manager = SvgCore.getGroupManager();
        if (manager == null) {
            return;
        }

        Group group = event.getGroup();
        VoicechatConnection connection = event.getConnection();
        if (group == null || group.getId() == null || connection == null || connection.getPlayer() == null) {
            return;
        }

        // Re-fetch + reconcile; skips revision bump when SVG already published this state.
        manager.onExternalMembershipEvent(
                connection.getPlayer().getUuid(),
                group.getId(),
                true
        );
    }

    private void onGroupLeft(LeaveGroupEvent event) {
        GroupManager manager = SvgCore.getGroupManager();
        if (manager == null) {
            return;
        }

        Group group = event.getGroup();
        VoicechatConnection connection = event.getConnection();
        if (connection == null || connection.getPlayer() == null) {
            return;
        }

        manager.onExternalMembershipEvent(
                connection.getPlayer().getUuid(),
                group == null ? null : group.getId(),
                false
        );
    }

    /**
     * Get the Server API of SVC
     * @return the server API, or null if the server hasn't started yet.
     */
    public VoicechatServerApi getVcServerApi() {
        return serverApi;
    }
}
