package io.github.theodoremeyer.spigotmc.simplevoicegeysertoo;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiosender.AudioSender;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Audio Sender to send Clients Audio to server
 */
public class SvgAudioSender {

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
    private AudioSender delegate;
    /**
     * The player the audio sender is associated with
     */
    private final Player player;
    /**
     * Audio Encoder to encode the audio for svc
     */
    private final OpusEncoder encoder;

    /**
     * Class Constructor. Creates and registers the audio sender
     * @param serverApi voicechat server api
     * @param playerUuid uuid of player registering sender for.
     */
    public SvgAudioSender(VoicechatServerApi serverApi, UUID playerUuid) {
        this.serverApi = serverApi;
        this.playerUuid = playerUuid;
        this.player = Bukkit.getPlayer(playerUuid);
        this.encoder = serverApi.createEncoder();

        VoicechatConnection connection = serverApi.getConnectionOf(playerUuid);

        if (player == null || !player.isOnline()) {
            SVGPlugin.log().warning("Player is offline: " + playerUuid);
            return;

        }

        if (connection == null) {
            SVGPlugin.log().warning("no svc connection for uuid: " + playerUuid);
            return;
        }

        if (connection.isInstalled()) {
            SVGPlugin.log().warning("Player: " + player.getName() + " has the mod installed." );
            player.sendMessage(ChatColor.RED + "You Can't Join SVG With The Mod Installed.");
            return;
        }

        if (SVGPlugin.getBridge().audioSenders.containsKey(playerUuid)) {
            SVGPlugin.log().warning("[VCBridge] SvgAudioSender already registered for: " + playerUuid);
            return;
        }

        this.delegate = serverApi.createAudioSender(connection); //create the sender itself
        boolean success = serverApi.registerAudioSender(delegate);

        if (success) {
            connection.setConnected(true);
            player.sendMessage(ChatColor.AQUA + "AudioSender Registered!");
            connection.setDisabled(false);
        } else {
            SVGPlugin.log().info("Failed to register SvgAudioSender for UUID: " + playerUuid);
            player.sendMessage(ChatColor.RED + "Failed to register AudioSender");

        }
    }

    /**
     * Sends Opus-encoded audio to the player if conditions are met.
     * @param pcmData data to send for player
     * @return true/false whether it failed or not
     */
    public boolean sendOpus(byte[] pcmData) {
        SVGPlugin.getInstance().debug( "AudioSender","received audio data from websocket!");
        if (player == null || !player.isOnline()) {
            SVGPlugin.log().warning("[AudioSender] Player not found or offline: " + playerUuid);
            return false;
        }

        VoicechatConnection connection = serverApi.getConnectionOf(playerUuid);
        if (connection == null) { //make sure player is online for SVC
            SVGPlugin.log().warning("[AudioSender] No voice chat connection for: " + player.getName());
            return false;
        } else if (connection.isInstalled()) {
            player.sendMessage(ChatColor.DARK_RED + "You have the mod Installed!");
            return false;
        }

        byte[] encoded;
        try {
            short[] pcmShorts = serverApi.getAudioConverter().bytesToShorts(pcmData);
            encoded = encoder.encode(pcmShorts); // PCM to Opus encoded
            if (encoded == null || encoded.length == 0) {
                SVGPlugin.log().warning("[AudioSender] Encoder returned empty data for: " + playerUuid);
                return false;
            }
        } catch (Exception e) {
            SVGPlugin.log().severe("[AudioSender] Encoding failed for: " + playerUuid + " - " + e.getMessage());
            SVGPlugin.getInstance().debug("AudioSender", "Encoding failed for " + playerUuid, e);
            e.printStackTrace();
            return false;
        } finally {
            if (encoder != null) {
                try {
                    encoder.resetState();
                } catch (Exception e) {
                    SVGPlugin.log().warning("[AudioSender] Failed to clean up encoder: " + e.getMessage());
                }
            }
        }

        boolean success = delegate.send(encoded);
        if (!success) {
            SVGPlugin.log().warning("[AudioSender] Failed to send audio for: " + playerUuid);
        } else {
            SVGPlugin.getInstance().debug("AudioSender","Sent Audio!");
        }
        return success;
    }

    /**
     * Closes the AudioSender, and ends decoder
     */
    public void unregister() {
        try {
            if (encoder != null) {
                encoder.resetState();
                encoder.close();
            }
        } catch (Exception e) {
            SVGPlugin.log().warning("[SvgAudioSender] Failed to close encoder for " + playerUuid + ": " + e.getMessage());
        }
        delegate.reset();
        serverApi.unregisterAudioSender(delegate); //end sender
        SVGPlugin.log().info("[SvgAudioSender] Unregistered sender for " + playerUuid);
        if (player != null) { player.sendMessage("audioSender unregistered."); }
    }
}