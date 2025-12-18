package io.github.theodoremeyer.spigotmc.simplevoicegeyser;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiolistener.PlayerAudioListener;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.RemoveGroupEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import io.github.theodoremeyer.spigotmc.simplevoicegeyser.audio.SvgAudioListener;
import io.github.theodoremeyer.spigotmc.simplevoicegeyser.audio.SvgAudioSender;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Class Bridging with Simple Voice Chat
 */
public class VoiceChatBridge implements VoicechatPlugin {

    /**
     * VoiceChatApi
     * @apiNote not currently used
     * @see VoicechatApi api
     */
    private VoicechatApi api;
    /**
     * The VoiceChatSeverApi
     * @see VoicechatServerApi server api
     */
    private VoicechatServerApi serverApi;
    /**
     * Map Containing all the audioSenders
     */
    public final Map<UUID, SvgAudioSender> audioSenders = new ConcurrentHashMap<>();
    /**
     * Map containing all the audioListeners
     */
    protected final Map<UUID, PlayerAudioListener> audioListeners = new ConcurrentHashMap<>();

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
        SVGPlugin.log().warning("[VCBridge] Registering events...");

        registration.registerEvent(VoicechatServerStartedEvent.class, this::onVoicechatStarted);
        registration.registerEvent(CreateGroupEvent.class, this::onGroupCreated);
        registration.registerEvent(RemoveGroupEvent.class, this::onGroupRemoved);
    }

    /**
     * Runs when SVC initializes this plugin
     * @param api the voicechatapi
     */
    @Override
    public void initialize(VoicechatApi api) {
        this.api = api;
        SVGPlugin.log().info("[VCBridge] VoiceChat API initialized");

    }

    /**
     * runs when the VoicechatServer starts
     * @param event the Server starting event
     */
    private void onVoicechatStarted(VoicechatServerStartedEvent event) {
        serverApi = event.getVoicechat(); //get the SVC api
        SVGPlugin.log().info("[VCBridge] Voice chat server started: " + serverApi);
        for (Group group : serverApi.getGroups()) {
            GroupManager.groups.putIfAbsent(group.getName(), group);
            SVGPlugin.log().info("[VCBridge] Loaded group: " + group.getName());
        }
    }

    /**
     * runs when a group is created, so we can store it in our local memory
     * @param event groupCreatedEvent
     */
    private void onGroupCreated(CreateGroupEvent event) {
        Group group = event.getGroup();
        if (group != null) {
            GroupManager.groups.put(group.getName(), group); //save the group to GroupManager
        }
    }

    /**
     * runs when a group is removed
     * @param event groupRemovedEvent
     */
    private void onGroupRemoved(RemoveGroupEvent event) {
        Group group = event.getGroup();
        if (group != null) {
            GroupManager.groups.remove(group.getName(), group); //remove the non-existent group from the group manager
        }
    }

    /**
     * Easy way to get the ServerApi.
     * @return The Voice chatServerApi
     */
    public VoicechatServerApi getVcServerApi() {
        return serverApi;
    }

    /**
     * Creates an AudioSender
     * @param uuid uuid to link sender too
     */
    public void registerAudioSender(UUID uuid) {
        if (serverApi == null) {
            SVGPlugin.log().warning("[VCBridge] Cannot register SvgAudioSender: Server API is null");
            return;
        }

        if (audioSenders.containsKey(uuid)) {
            SVGPlugin.log().warning("[VCBridge] SvgAudioSender already registered for: " + uuid);
            return;
        }

        try {
            SvgAudioSender sender = new SvgAudioSender(serverApi, uuid); //create the sender
            audioSenders.put(uuid, sender);
            SVGPlugin.log().info("[VCBridge] SvgAudioSender created and registered for: " + uuid);
        } catch (RuntimeException e) {
            SVGPlugin.log().warning("[VCBridge] Failed to register SvgAudioSender for: " + uuid + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Stops an AudioSender
     * @param uuid uuid of AudioSender to stop
     */
    public void unregisterAudioSender(UUID uuid) {
        SvgAudioSender sender = audioSenders.remove(uuid); //remove the sender from the map
        if (sender != null) {
            sender.unregister(); //unregister the sender
            SVGPlugin.log().info("[VCBridge] SvgAudioSender unregistered for: " + uuid);
        } else {
            SVGPlugin.log().warning("[VCBridge] No SvgAudioSender found to unregister for: " + uuid);
        }
    }

    /**
     * Creates an AudioListener
     * @param uuid uuid to associate listener to
     */
    public void registerAudioListener(UUID uuid) {
        if (serverApi == null) {
            SVGPlugin.log().warning("[VCBridge] Cannot register listener: Server API is null");
            return;
        }

        if (audioListeners.containsKey(uuid)) {
            SVGPlugin.log().info("[VCBridge] Listener already registered for: " + uuid);
            return;
        }

        SvgAudioListener listener = new SvgAudioListener(uuid); //create a new audio listener
        listener.registerListener(serverApi);
        audioListeners.put(uuid, listener); //add it to the listener map
        SVGPlugin.log().info("[VCBridge] Registered audio listener for: " + uuid);
    }

    /**
     * Stops an AudioListener
     * @param uuid uuid of the audioListener to stop
     */
    public void unregisterAudioListener(UUID uuid) {
        if (serverApi == null) {
            SVGPlugin.log().warning("[VCBridge] Cannot unregister listener: Server API is null");
            return;
        }

        PlayerAudioListener listener = audioListeners.remove(uuid); //remove the listener from the map
        if (listener != null) {
            serverApi.unregisterAudioListener(listener); //unregister the listener
            SVGPlugin.log().info("[VCBridge] Unregistered audio listener for: " + uuid);
        } else {
            SVGPlugin.log().warning("[VCBridge] No audio listener found for: " + uuid);
        }
    }
}
