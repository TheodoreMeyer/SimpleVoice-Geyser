package io.github.theodoremeyer.spigotmc.simplevoicegeyser.server;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.audiosender.AudioSender;
import io.github.theodoremeyer.spigotmc.simplevoicegeyser.*;
import io.github.theodoremeyer.spigotmc.simplevoicegeyser.audio.SvgAudioSender;
import io.github.theodoremeyer.spigotmc.simplevoicegeyser.geyser.GeyserHook;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Duration;
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
    private Player player;
    /**
     * Whether the user has logged in
     */
    private boolean authenticated = false;
    /**
     * AudioSender associated with this.
     */
    private SvgAudioSender audioSender;
    /**
     * The Main Class.
     */
    private final SVGPlugin plugin;

    protected JettyWebSocket(SVGPlugin p) {
        this.plugin = p;
    }


    /**
     * When the client connects.
     * @param session the websocket session associated with this class.
     */
    @OnWebSocketConnect
    public void onConnect(Session session) {
        this.session = session;
        session.setIdleTimeout(Duration.ofMinutes(plugin.getConfig().getInt("client.idletimeout", 4)));
        SVGPlugin.log().info("[Websocket] WebSocket connected: " + session.getRemoteAddress());
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

        message = message.trim().toLowerCase();

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
                SVGPlugin.log().severe("[VCBridge] code: 1, Exception: " + e.getMessage());
                SVGPlugin.debug("VCBridge", "error reading client data", e);
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

        byte[] pcmData = new byte[length];
        System.arraycopy(buffer, offset, pcmData, 0, length);

        if (audioSender != null) { //make sure the sender is not null
            audioSender.sendOpus(pcmData); //send the audio data
        }
    }

    /**
     * What to do when the websocket closes
     * @param statusCode code of how it exited
     * @param reason why the websocket closed
     */
    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        String username = PlayerVcPswd.getUsernameFromUUID(uuid);
        String displayName = (username != null) ? username : uuid.toString();

        SVGPlugin.log().info("[WebSocket] WebSocket for " + displayName + " closed: " + statusCode + " - " + reason);
        if (uuid != null) { //make sure the uuid for the session is not null, needed to close senders/listeners
            SVGPlugin.getBridge().unregisterAudioSender(uuid);
            SVGPlugin.getBridge().unregisterAudioListener(uuid);
        } else {
            SVGPlugin.log().warning("[WebSocket] Disconnected: unknown client (" + reason + ")");
        }
        WebSocketManager.removeClient(uuid);
    }

    /**
     * What to do on a websocket error
     * @param error the error thrown
     */
    @OnWebSocketError
    public void onError(Throwable error) {
        SVGPlugin.debug("WebSocket", "websocket error", error);
        SVGPlugin.log().info("Error: " + error.getMessage());
    }

    private void join(JSONObject json) {
        String username = json.getString("username");
        String password = json.optString("password", "");
        UUID storedUuid = PlayerVcPswd.getStoredUUID(username); //get the uuid to associate with this session

        if (storedUuid == null) {
            closeOnError("Player " + username + " not found. Use /svg pswd [password] in-game to register.", false);
            return;
        }
        this.uuid = storedUuid;

        Boolean bedrock = GeyserHook.isBedrock(storedUuid);
        if (bedrock == null) {
            // Geyser/Floodgate not installed
            if (plugin.getConfig().getBoolean("client.requireBedrock", false)) {
                SVGPlugin.log().warning("Unable to enforce: client.requireBedrock. Please install floodgate or geyser");
                return;
            }
        } else if (!bedrock) {
            if (plugin.getConfig().getBoolean("client.requireBedrock", false)) {
                closeOnError("You must be a Bedrock player to join!", false);
                return;
            }
        }

        //see if the player's password is set.
        if (!PlayerVcPswd.isPasswordSet(username)) {
            closeOnError("Password not set. Use /svg pswd [password] in-game.", false);
            return;
        }

        if (!PlayerVcPswd.validatePassword(username, password)) { //validate the player's password from form input
            closeOnError("Incorrect password!", false);
            return;
        }

        if (!WebSocketManager.addClient(uuid, this.session)) { //add this session to the list of active sessions
            closeOnError("Incorrect password!", false);
            return;
        }

        //get timeout numbers for the message.
        int timeout = plugin.getVcTimeout();
        long delayInTicks = timeout * 20L;

        authenticated = true;
        sendMessage("status", "Connected as " + username + ".", false); //Make sure to join server within " + timeout + " seconds!");

        SVGPlugin.log().info("[WebSocket] " + username + " joined with UUID: " + uuid);

        // Schedule timeout if player never joins. Currently, disabled.
        //Bukkit.getScheduler().runTaskLater(plugin, () -> {
        this.player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            closeOnError("Timeout: You didn’t join the server in time.", false);
            return;
        }
        if (!player.hasPermission("svg.vc.join")) { //make sure they are allowed to join the vc
            closeOnError("Access Denied. You may have been banned from vc.", true);
            return;
        }

        VoicechatConnection connection = SVGPlugin.getBridge().getVcServerApi().getConnectionOf(uuid);
        if (connection == null || connection.isInstalled()) {
            closeOnError("Can't Join server with mod installed or Connection is Null", false);
            return;
        }

        if (plugin.getConfig().getBoolean("server.group.default.enabled")) {
            String gPswd = plugin.getConfig().getString("server.group.default.password", "1a2b");

            SVGPlugin.getGroupManager().createGroup(player, "Svg", gPswd, Group.Type.OPEN, false, true); //add player to a default group
        }

        SVGPlugin.getBridge().registerAudioListener(uuid); //register the players audio sender
        this.audioSender = SVGPlugin.getBridge().registerAudioSender(uuid); //register the players audio sender
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
            String name = PlayerVcPswd.getUsernameFromUUID(uuid);
            if (player != null) {
                sendMessage("chat", "You" + chatMessage, false);
                player.chat("[Web Chat] " + (name != null ? name : uuid) + ": " + ChatColor.BLUE + chatMessage);
            } else {
                sendMessage("error", "You are Not in Game", false);
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
            e.printStackTrace();
        }


        if (player != null) {
            player.sendMessage(SVGPlugin.PREFIX + ChatColor.translateAlternateColorCodes('&', message));
        }
        if (log) {
            SVGPlugin.log().info("[WebSocket] " + type + ": " + uuid + ": " + message);
        }
    }

    private void closeOnError(String message, boolean log) {
        sendMessage("error", message, log);
        session.close();
    }
}
