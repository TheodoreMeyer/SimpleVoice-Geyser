package io.github.theodoremeyer.spigotmc.simplevoicegeysertoo;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiolistener.PlayerAudioListener;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.packets.SoundPacket;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.eclipse.jetty.websocket.api.Session;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * The Class Sending Audio to Client
 */
public class SvgAudioListener implements PlayerAudioListener {

    private final UUID listenerId;
    private VoicechatServerApi serverapi;

    /**
     * Class constructor to set id
     * @param listenerId the id of this listener
     */
    public SvgAudioListener(UUID listenerId) {
        this.listenerId = listenerId;
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
        byte[] opusData = soundPacket.getOpusEncodedData();

        Session session = WebSocketManager.clients.get(listenerId);
        if (session != null && session.isOpen()) {
            OpusDecoder decoder = null;
            try {
                // Decode opus to raw PCM (16-bit signed, little-endian)
                decoder = SVGPlugin.getBridge().getVcServerApi().createDecoder();
                short[] pcm = decoder.decode(opusData);

                byte[] bytes = serverapi.getAudioConverter().shortsToBytes(pcm); //convert audio to a usable type

                session.getRemote().sendBytes(ByteBuffer.wrap(bytes)); //send the decoded audio
            } catch (Exception e) {
                SVGPlugin.log().warning("Error sending audio to client" + listenerId);
                SVGPlugin.getInstance().debug("AudioListener", "Error sending audio to client" + listenerId, e);
                e.printStackTrace();
            }  finally {
                if (decoder != null && !decoder.isClosed()) {
                    decoder.close();
                }
            }
        } else {
            SVGPlugin.log().info("Session Not Open");
            SVGPlugin.getBridge().unregisterAudioListener(listenerId);
        }
    }


    /**
     * Registers the listener with the VoiceChat server.
     * May be moved to class constructor
     * @param serverApi Vcs api to register with.
     */
    public void registerListener(VoicechatServerApi serverApi) {
        this.serverapi = serverApi;
        PlayerAudioListener listener = serverApi.playerAudioListenerBuilder()
                .setPlayer(listenerId)
                .setPacketListener(this::onAudioReceived)
                .build();

        if (serverApi.registerAudioListener(listener)) { //make sure SVC successfully registered
            SVGPlugin.log().info("[VCBridge] Registered audio listener for: " + listenerId);
            Player player = Bukkit.getPlayer(listenerId);
            player.sendMessage(ChatColor.AQUA + "Registered Audio listener!");
        } else {
            SVGPlugin.log().warning("[VCBridge] Failed to register audio listener for: " + listenerId);
            Bukkit.getPlayer(listenerId).sendMessage(ChatColor.RED + "Failed to register audio listener");
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
