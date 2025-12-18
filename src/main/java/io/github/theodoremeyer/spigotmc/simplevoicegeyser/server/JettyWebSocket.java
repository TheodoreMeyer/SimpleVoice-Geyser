package io.github.theodoremeyer.spigotmc.simplevoicegeyser.server;

import io.github.theodoremeyer.spigotmc.simplevoicegeyser.*;
import io.github.theodoremeyer.spigotmc.simplevoicegeyser.audio.SvgAudioSender;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.json.JSONObject;

import java.time.Duration;
import java.util.UUID;

/**
 * Websocket session Class.
 */
@WebSocket
public class JettyWebSocket {
    protected Session session;
    private UUID uuid;
    private boolean authenticated = false;
    private Player player;

    /**
     * When the client connects.
     * @param session the websocket session associated with this class.
     */
    @OnWebSocketConnect
    public void onConnect(Session session) {
        this.session = session;
        session.setIdleTimeout(Duration.ofMinutes(4)); //set timeout to a high value, so it doesn't quickly kick the client for inactivity
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

        message = message.trim();

        //reject non JSON messages early
        if (!message.startsWith("{")) {
            WebSocketManager.sendJson(uuid, "error", "Invalid input. Expected a JSON object.");
            return;
        }

        try {
            JSONObject json = new JSONObject(message);
            String type = json.getString("type");

            //have player join vc
            if ("join".equalsIgnoreCase(type)) { //handle the join form
                String username = json.getString("username");
                String password = json.optString("password", "");
                UUID storedUuid = PlayerVcPswd.getStoredUUID(username); //get the uuid to associate with this session
                if (storedUuid == null) {
                    WebSocketManager.sendJson(uuid, "error", "Player '" + username + "' not found. Use /svg pswd [password] in-game to register.");
                    SVGPlugin.log().warning("[WebSocket] Player '" + username + "' not found in stored data.");
                    session.close();
                    return;
                }
                this.uuid = storedUuid;
                SVGPlugin.log().info("[WebSocket] Player found. UUID: " + uuid);

                //see if the player's password is set.
                if (!PlayerVcPswd.isPasswordSet(username)) {
                    JSONObject errorJson = new JSONObject();
                    errorJson.put("type", "error");
                    errorJson.put("message", "Password not set. Use /svg pswd [password] in-game.");
                    session.getRemote().sendString(errorJson.toString());
                    session.close();
                    return;
                }

                if (!PlayerVcPswd.validatePassword(username, password)) { //validate the player's password from form input
                    JSONObject errorJson = new JSONObject();
                    errorJson.put("type", "error");
                    errorJson.put("message", "Incorrect password. Try again.");
                    session.getRemote().sendString(errorJson.toString());
                    session.close();
                    return;
                }

                if (!WebSocketManager.addClient(uuid, this.session)) { //add this session to the list of active sessions
                    WebSocketManager.sendJson(uuid, "error", "Already connected");
                    session.close();
                    return;
                }

                //get timeout numbers for the message.
                int timeout = SVGPlugin.getInstance().getVcTimeout();
                long delayInTicks = timeout * 20L;

                authenticated = true;
                JSONObject successJson = new JSONObject();
                successJson.put("type", "status");
                successJson.put("message", "Connected as " + username + ". Make sure to join server within " + timeout + " seconds!");
                session.getRemote().sendString(successJson.toString());
                SVGPlugin.log().info("[WebSocket] " + username + " joined with UUID: " + uuid);

                // Schedule timeout if player never joins. Currently is disabled
                //Bukkit.getScheduler().runTaskLater(SVGPlugin.getInstance(), () -> {
                this.player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    SVGPlugin.log().info("[WebSocket] " + username + " did not join in time. Disconnecting.");
                    WebSocketManager.removeClient(uuid);
                    WebSocketManager.sendJson(uuid, "error", "Timeout: You didn’t join the server in time.");
                    session.close();
                    return;
                }
                if (!player.hasPermission("svg.vc.join")) { //make sure they are allowed to join the vc
                    SVGPlugin.log().info("[WebSocket] " + username + " did not have permissions to join VC");
                    WebSocketManager.sendJson(uuid, "error", "Access denied. You don't have the permission to join, or have been banned.");
                    session.close();
                }
                SVGPlugin.getBridge().registerAudioListener(uuid); //register the players audio sender
                GroupManager.createGroup(Bukkit.getPlayer(uuid), "Svg", "1a2b", "open", false, true); //add player to a default group
                SVGPlugin.getBridge().registerAudioSender(uuid); //register the players audio sender
                // Currently disabled.
               // }, delayInTicks);
            }

            //handle chat inputs.
            else if ("chat".equalsIgnoreCase(type)) {
                String chatMessage = json.optString("message", "").trim();
                if (!authenticated) { //if they are signed in
                   WebSocketManager.sendJson(uuid, "error", "You must be authenticated.");
                   SVGPlugin.log().info(chatMessage);
                   return;
                }

                if (!chatMessage.isEmpty()) {
                    String name = PlayerVcPswd.getUsernameFromUUID(uuid);
                    if (player != null) {
                        WebSocketManager.sendJson(uuid, "chat", "You" + chatMessage);
                        player.chat("[Web Chat] " + (name != null ? name : uuid) + ": " + ChatColor.BLUE + chatMessage);
                    } else {
                        WebSocketManager.sendJson(uuid, "error", "You are Not in Game");
                    }
                }
            }

            //handle unknown types
            else {
                session.getRemote().sendString(new JSONObject()
                    .put("type", "error")
                    .put("message", "Unknown type: " + type).toString());
            }

        } catch (Exception e) {
                SVGPlugin.log().severe("[VCBridge] code: 1, Exception: " + e.getMessage());
                SVGPlugin.getInstance().debug("VCBridge", "error reading client data", e);
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
        SVGPlugin.getInstance().debug("Websocket","Received audio data from client");
        if (!authenticated || uuid == null) return; //make sure they signed in

        byte[] pcmData = new byte[length];
        System.arraycopy(buffer, offset, pcmData, 0, length);

        SvgAudioSender sender = SVGPlugin.getBridge().audioSenders.get(uuid);
        if (sender != null) { //make sure the sender is not null
            boolean success = sender.sendOpus(pcmData); //send teh audiodata
            if (!success) {
                SVGPlugin.log().warning("Failed to send Audio to audioSender for" + uuid);
            }
        }
    }

    /**
     * What to do when the websocket closes
     * @param statusCode code of how it exited
     * @param reason why the websocket closed
     */
    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        SVGPlugin.log().info("[WebSocket] WebSocket closed: " + statusCode + " - " + reason);
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
        SVGPlugin.log().warning("[Websocket]: An error occurred. code: 2");
        SVGPlugin.getInstance().debug("WebSocket", "websocket error", error);
        SVGPlugin.log().info("Error: " + error.getMessage());
    }
}
