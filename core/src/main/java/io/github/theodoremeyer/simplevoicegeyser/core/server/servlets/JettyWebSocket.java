package io.github.theodoremeyer.simplevoicegeyser.core.server.servlets;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import io.github.theodoremeyer.simplevoicegeyser.core.PlayerVcPswd;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.SvgAudioSender;
import io.github.theodoremeyer.simplevoicegeyser.core.geyser.GeyserHook;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

/**
 * Websocket session Class.
 */
@WebSocket
public final class JettyWebSocket {

    /**
     * The Session that is the audio client.
     */
    private Session session;

    /**
     * UUID associated with this websocket/user
     */
    private UUID uuid;
    /**
     * Player that this websocket does audio for
     */
    private SvgPlayer player;
    /**
     * Whether the user has logged in
     */
    private boolean authenticated = false;
    /**
     * AudioSender associated with this.
     */
    private SvgAudioSender audioSender;

    /**
     * Create a websocket connection with a client
     */
    public JettyWebSocket() {}

    /**
     * When the client connects.
     * @param session the websocket session associated with this class.
     */
    @OnWebSocketConnect
    public void onConnect(Session session) {
        this.session = session;
        session.setIdleTimeout(Duration.ofMinutes(SvgCore.getConfig().IDLE_TIMEOUT.get()));
        SvgCore.getLogger().info("[Websocket] WebSocket connected: " + session.getRemoteAddress());
    }

    /**
     * Handles all NON vc messages sent by client.
     * @param message message sent by client.
     */
    @OnWebSocketMessage
    public void onMessage(String message) {

        //ignore empty messages
        if (message == null || message.trim().isEmpty()) { //make sure the message is not empty
            return;
        }

        message = message.trim();

        //reject non JSON messages early
        if (!message.startsWith("{")) {
            sendMessage("error", "Invalid input. Expected a JSON object.", false);
            return;
        }

        try {
            JSONObject json = new JSONObject(message);
            String type = json.getString("type");

            switch (type) {
                case "join": { //handle the join form
                    join(json);
                    return;
                }

                case "chat": { //handle chat inputs.
                    chat(json);
                    return;
                }

                //handle unknown types
                case null, default:
                    sendMessage("error", "unknown type: " + type, false);
            }

        } catch (Exception e) {
            SvgCore.getLogger().severe("[VCBridge] code: 1, Exception: " + e.getMessage());
            SvgCore.debug("VCBridge", "error reading client data", e);
        }

    }

    /**
     * this copy is to handle audio data from the client.
     * I Don't really know too much about how to get pcmData from sent byte, all I know is that this is how Simple Voice Chat, and research wants me to do it
     * @param buffer the byte to process
     * @param offset the offset of the byte
     * @param length the int.
     */
    @OnWebSocketMessage
    public void onMessage(byte[] buffer, int offset, int length) {
        if (!authenticated || uuid == null) return; //make sure they signed in

        //byte[] pcmData = new byte[length];
        //System.arraycopy(buffer, offset, pcmData, 0, length);

        if (audioSender != null) { //make sure the sender is not null
            //audioSender.sendOpus(pcmData); //send the audio data
            audioSender.sendOpus(Arrays.copyOfRange(buffer, offset, offset + length));
        }
    }

    /**
     * What to do when the websocket closes
     * @param statusCode code of how it exited
     * @param reason why the websocket closed
     */
    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        if (uuid != null) { //make sure the uuid for the session is not null, needed to close senders/listeners
            String username = SvgCore.getPasswordManager().getUsernameFromUUID(uuid);
            String displayName = (username != null) ? username : uuid.toString();
            SvgCore.getLogger().info("[WebSocket] WebSocket for " + displayName + " closed: " + statusCode + " - " + reason);

            SvgCore.getBridge().unregisterAudioSender(uuid);
            SvgCore.getBridge().unregisterAudioListener(uuid);
        } else {
            SvgCore.getLogger().warning("[WebSocket] Disconnected: unknown client for " + reason + ".");
        }
        SvgCore.getWsManager().removeClient(uuid, session);
    }

    /**
     * What to do on a websocket error
     * @param error the error thrown
     */
    @OnWebSocketError
    public void onError(Throwable error) {
        SvgCore.debug("WebSocket", "websocket error", error);
        SvgCore.getLogger().info("Error: " + error.getMessage());
    }

    private void join(@NonNull JSONObject json) {
        PlayerVcPswd playerVcPswd = SvgCore.getPasswordManager();

        String username = json.getString("username");
        String password = json.optString("password", "");
        UUID storedUuid = playerVcPswd.getStoredUUID(username); //get the uuid to associate with this session

        if (storedUuid == null) {
            closeOnError("Player " + username + " not found. Use /svg pswd [password] in-game to register.", false);
            return;
        }
        this.uuid = storedUuid;

        Boolean bedrock = GeyserHook.isBedrock(storedUuid);
        boolean bedrockRequired = SvgCore.getConfig().REQUIRE_BEDROCK.get();

        if (bedrock == null) {
            // Geyser/Floodgate not installed
            if (bedrockRequired) {
                SvgCore.getLogger().warning("Unable to enforce: client.requireBedrock. Please install floodgate or geyser");
            }
        } else if (!bedrock) {
            if (bedrockRequired) {
                closeOnError("Access Denied: You must be a Bedrock player to join!", false);
                return;
            }
        }

        //see if the player's password is set.
        if (!playerVcPswd.isPasswordSet(username)) {
            closeOnError("Password not set. Use /svg pswd [password] in-game.", false);
            return;
        }

        if (!playerVcPswd.validatePassword(username, password)) { //validate the player's password from form input
            closeOnError("Access Denied: Incorrect password!", false);
            return;
        }

        if (!SvgCore.getWsManager().addClient(uuid, this.session)) { //add this session to the list of active sessions
            closeOnError("Access Denied: Failed to Join.", false);
            return;
        }

        //get timeout numbers for the message.
        int timeout = SvgCore.getConfig().VC_TIMEOUT.get();//plugin.getConfig().getInt("client.vctimeout", 30);
        long delayInTicks = timeout * 20L;

        authenticated = true;
        sendMessage("status", "Connected as " + username + ".", false); //Make sure to join server within " + timeout + " seconds!");

        SvgCore.getLogger().info("[WebSocket] " + username + " joined with UUID: " + uuid);

        // Schedule timeout if player never joins. Currently, disabled.
        //Bukkit.getScheduler().runTaskLater(plugin, () -> {
        this.player = SvgCore.getPlayerManager().getPlayer(uuid);
        if (player == null) {
            closeOnError("Timeout: You didn’t join the server in time.", false);
            return;
        }
        if (!player.hasPermission("svg.vc.join")) { //make sure they are allowed to join the vc
            closeOnError("Access Denied. You may have been banned from vc.", true);
            return;
        }

        VoicechatConnection connection = SvgCore.getBridge().getVcServerApi().getConnectionOf(uuid);
        if (connection == null || connection.isInstalled()) {
            closeOnError("Access Denies: Can't Join server with mod installed or Connection is Null", false);
            return;
        }

        // Add player to default group if enabled
        if (SvgCore.getConfig().DEFAULT_GROUP_ENABLED.get()) {
            String gPswd = SvgCore.getConfig().DEFAULT_GROUP_PASSWORD.get();
            SvgCore.getGroupManager().createGroup(player, "Svg", gPswd, Group.Type.OPEN, false, true); //add player to a default group
        }

        SvgCore.getBridge().registerAudioListener(uuid, session); //register the players audio sender
        this.audioSender = SvgCore.getBridge().registerAudioSender(uuid); //register the players audio sender
        // Currently disabled.
        // }, delayInTicks);
    }

    private void chat(JSONObject json) {
        String chatMessage = json.optString("message", "").trim();
        if (!authenticated) { //if they are signed in
            sendMessage("error", "You must be authenticated.", false);
            return;
        }

        if (!chatMessage.isEmpty()) {
            String savedName = SvgCore.getPasswordManager().getUsernameFromUUID(uuid);
            String displayName = (player != null) ? player.getName() :
                    savedName != null ? savedName : uuid.toString();

            if (player != null) {
                sendMessage("chat", "You" + chatMessage, false);
                player.chat("[Web Chat] " + displayName + ": " + SvgColor.BLUE + chatMessage);
            } else {
                sendMessage("error", "You are Not in Game. Sent message", false);
                for (SvgPlayer p : SvgCore.getPlayerManager().getAllPlayers()) {
                    p.sendMessage("[Web Chat] " + displayName + ": " + SvgColor.BLUE + chatMessage);
                }
            }
        }
    }

    private void sendMessage(String type, String message, boolean log) {

        JSONObject json = new JSONObject();
        json.put("type", type);
        json.put("message", message);
        try {
            session.getRemote().sendString(json.toString());
        } catch (IOException e) {
            SvgCore.getLogger().severe(e.toString());
        }


        if (player != null) {
            player.sendMessage(SvgCore.getPrefix() + SvgColor.translateAltColorCodes('&', message));
        }
        if (log) {
            SvgCore.getLogger().info("[WebSocket] " + type + ": " + uuid + ": " + message);
        }
    }

    private void closeOnError(String message, boolean log) {
        sendMessage("error", message, log);
        session.close();
    }
}
