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

    /**
     * The VoiceChatSeverApi
     * @see VoicechatServerApi server api
     */
    private VoicechatServerApi serverApi;

    /**
     * Initializes the plugin's connection with SVC.
     */
    public VoiceChatBridge() {}

    /**
     * The SVC plugin id
     * @return The plugin id: SimpleVoice-Geyser
     */
    @Override
    public String getPluginId() {
        return "SimpleVoice-Geyser";
    }

    /**
     * Registers events with simple voice chat
     * @param registration the event registrator
     */
    @Override
    public void registerEvents(EventRegistration registration) {
        SvgCore.getLogger().warning("[VCBridge] Registering events...");

        registration.registerEvent(VoicechatServerStartedEvent.class, this::onVoicechatStarted);
        registration.registerEvent(CreateGroupEvent.class, this::onGroupCreated);
        registration.registerEvent(RemoveGroupEvent.class, this::onGroupRemoved);
    }

    /**
     * Runs when SVC initializes this plugin
     * @param api the VoicechatApi
     */
    @Override
    public void initialize(VoicechatApi api) {
        SvgCore.getLogger().info("[VCBridge] VoiceChat API initialized");

    }

    /**
     * runs when the VoicechatServer starts
     * @param event the Server starting event
     */
    private void onVoicechatStarted(VoicechatServerStartedEvent event) {
        serverApi = event.getVoicechat(); //get the SVC api
        SvgCore.getLogger().info("[VCBridge] Voice chat server started: " + serverApi);
        for (Group group : serverApi.getGroups()) {
            SvgCore.getGroupManager().addGroup(group);
            SvgCore.getLogger().info("[VCBridge] Loaded group: " + group.getName());
        }
    }

    /**
     * runs when a group is created, so we can store it in our local memory
     * @param event groupCreatedEvent
     */
    private void onGroupCreated(CreateGroupEvent event) {
        Group group = event.getGroup();
        if (group != null) {
            SvgCore.getGroupManager().addGroup(group); //save the group to GroupManager
        }
    }

    /**
     * runs when a group is removed
     * @param event groupRemovedEvent
     */
    private void onGroupRemoved(RemoveGroupEvent event) {
        Group group = event.getGroup();
        if (group != null) {
            SvgCore.getGroupManager().removeGroup(group); //remove the non-existent group from the group manager
        }
    }

    /**
     * Easy way to get the ServerApi.
     * @return The Voice chatServerApi
     */
    public VoicechatServerApi getVcServerApi() {
        return serverApi;
    }
}
