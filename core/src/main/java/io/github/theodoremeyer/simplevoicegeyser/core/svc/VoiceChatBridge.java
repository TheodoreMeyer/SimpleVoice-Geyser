package io.github.theodoremeyer.simplevoicegeyser.core.svc;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.RemoveGroupEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;

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
