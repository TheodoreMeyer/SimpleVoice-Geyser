package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiolistener.PlayerAudioListener;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.packets.SoundPacket;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.thread.AudioThread;
import org.eclipse.jetty.websocket.api.Session;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * The Class Sending Audio to Client
 */
public final class SvgAudioListener implements PlayerAudioListener {

    private final UUID listenerId;
    private final VoicechatServerApi serverApi;
    private final OpusDecoder decoder;

    /**
     * Session, used for less method checks
     */
    private final Session session;

    /**
     * Class constructor to set id
     * @param listenerId the id of this listener
     */
    public SvgAudioListener(UUID listenerId, Session session, VoicechatServerApi serverApi) {
        this.listenerId = listenerId;
        this.session = session;
        this.serverApi = serverApi;

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
        SvgCore.debug("AudioListener", "received audio from SVG server!");

        if (session.isOpen()) {

            byte[] opusData = soundPacket.getOpusEncodedData();

            AudioThread.execute(() -> {
                try {
                    short[] pcm = decoder.decode(opusData);
                    byte[] bytes = serverApi.getAudioConverter().shortsToBytes(pcm); //convert audio to a usable type
                    session.getRemote().sendBytes(ByteBuffer.wrap(bytes));//send the decoded audio
                    SvgCore.debug("AudioListener", "Sent audio to websocket client!");
                } catch (Exception e) {
                    SvgCore.debug("AudioListener", "Error sending audio to client" + listenerId, e);
                }
            });
        } else {
            SvgCore.debug("AudioListener","Session Not Open.");
            SvgCore.getBridge().unregisterAudioListener(listenerId);
        }
    }


    /**
     * Registers the listener with the VoiceChat server.
     * May be moved to class constructor
     */
    public void registerListener() {
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
        } else {
            SvgCore.getLogger().warning("[VCBridge] Failed to register audio listener for: " + listenerId);
            if (player != null) {
                player.sendMessage(SvgCore.getPrefix() + SvgColor.RED + "Failed to register Audio Listener.");
            }
        }
    }

    /**
     * Closes the decoder
     */
    public void unRegister() {
        decoder.resetState();
        decoder.close();
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
