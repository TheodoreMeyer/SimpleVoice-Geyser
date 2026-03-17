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
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.SvgAudioListener;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.SvgAudioSender;
import org.eclipse.jetty.websocket.api.Session;

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
    private final Map<UUID, SvgAudioSender> audioSenders = new ConcurrentHashMap<>();
    /**
     * Map containing all the audioListeners
     */
    private final Map<UUID, SvgAudioListener> audioListeners = new ConcurrentHashMap<>();

    private final SvgCore core;

    /**
     * Initializes the plugin's connection with SVC.
     * @param core SVG plugin
     */
    public VoiceChatBridge(SvgCore core) {
        this.core = core;
    }

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
        this.api = api;
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

    /**
     * Creates an AudioSender
     * @param uuid uuid to link sender too
     * @return the registered Sender
     */
    public SvgAudioSender registerAudioSender(UUID uuid) {
        if (serverApi == null) {
            SvgCore.getLogger().warning("[VCBridge] Cannot register SvgAudioSender: Server API is null");
            return null;
        }

        if (audioSenders.containsKey(uuid)) {
            SvgCore.getLogger().warning("[VCBridge] SvgAudioSender already registered for: " + uuid);
            return null;
        }

        SvgPlayer SvgPlayer = SvgCore.getPlayerManager().getPlayer(uuid);
        if (SvgPlayer == null) throw  new IllegalStateException("SvgPlayer not found: " + uuid);

        try {
            SvgAudioSender sender = new SvgAudioSender(serverApi, uuid); //create the sender
            audioSenders.put(uuid, sender);
            SvgCore.debug("VCBridge", "SvgAudioSender created and registered for: " + uuid);
            SvgPlayer.sendMessage(SvgCore.getPrefix() + SvgColor.AQUA + "AudioSender Registered!");

            return sender;
        } catch (RuntimeException e) {
            SvgCore.debug("VCBridge", "Unable to register AudioSender for: " + uuid, e);
            SvgPlayer.sendMessage(SvgCore.getPrefix() + SvgColor.RED + "Failed to register AudioSender");
        }
        return null;
    }

    /**
     * Stops an AudioSender
     * @param uuid uuid of AudioSender to stop
     */
    public void unregisterAudioSender(UUID uuid) {
        SvgAudioSender sender = audioSenders.remove(uuid); //remove the sender from the map

        SvgPlayer SvgPlayer = SvgCore.getPlayerManager().getPlayer(uuid);

        if (sender != null) {
            sender.unregister(); //unregister the sender
            SvgCore.debug("VCBridge", "SvgAudioSender unregistered for: " + uuid);
            if (SvgPlayer != null) { SvgPlayer.sendMessage(SvgCore.getPrefix() + "audioSender unregistered."); }
        } else {
            if (SvgPlayer != null) {
                SvgCore.getLogger().warning("[VCBridge] No SvgAudioSender found to unregister for: " + uuid);
            } else {
                SvgCore.debug("VCBridge", "No SvgAudioSender found to unregister for: " + uuid);
            }
        }
    }

    /**
     * Creates an AudioListener
     * @param uuid uuid to associate listener to
     * @param session the associated session of the player
     */
    public void registerAudioListener(UUID uuid, Session session) {
        if (serverApi == null) {
            SvgCore.getLogger().warning("[VCBridge] Cannot register listener: Server API is null");
            return;
        }

        if (audioListeners.containsKey(uuid)) {
            SvgCore.getLogger().info("[VCBridge] Listener already registered for: " + uuid);
            return;
        }

        SvgAudioListener listener = new SvgAudioListener(uuid, session, serverApi); //create a new audio listener
        listener.registerListener();
        audioListeners.put(uuid, listener); //add it to the listener map
        SvgCore.debug("VCBridge", "Registered audio listener for: " + uuid);
    }

    /**
     * Stops an AudioListener
     * @param uuid uuid of the audioListener to stop
     */
    public void unregisterAudioListener(UUID uuid) {
        if (serverApi == null) {
            SvgCore.getLogger().warning("[VCBridge] Cannot unregister listener: Server API is null");
            return;
        }

        SvgAudioListener listener = audioListeners.remove(uuid); //remove the listener from the map
        if (listener != null) {
            serverApi.unregisterAudioListener(listener); //unregister the listener
            SvgCore.debug("VCBridge", "Unregistered audio listener for: " + uuid);
            listener.unRegister();
        } else {
            if (SvgCore.getPlayerManager().getPlayer(uuid) != null) {
                SvgCore.getLogger().warning("[VCBridge] No audio listener found for: " + uuid);
            } else {
                SvgCore.debug("VCBridge", "No audio listener found for: " + uuid);
            }
        }
    }
}
