package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.SvgConnection;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Broadcasts the online player roster so web clients can show Adjust Volumes rows.
 */
public final class VoicePlayersBroadcaster {

    private VoicePlayersBroadcaster() {}

    /**
     * Send a personalized {@code voice_players} packet to every authenticated websocket
     * (each client excludes its own UUID).
     */
    public static void broadcast() {
        for (SvgConnection connection : SvgCore.getConnectionManager().getAll()) {
            sendTo(connection);
        }
    }

    /**
     * Send the current roster to one connection (excluding that client's UUID).
     */
    public static void sendTo(SvgConnection connection) {
        if (connection == null) {
            return;
        }
        connection.sendJson(buildPacket(connection.getUuid()));
    }

    static JSONObject buildPacket(UUID excludeUuid) {
        JSONArray players = new JSONArray();
        for (SvgPlayer player : SvgCore.getPlayerManager().getAllPlayers()) {
            if (excludeUuid != null && excludeUuid.equals(player.getUniqueId())) {
                continue;
            }
            JSONObject entry = new JSONObject();
            entry.put("uuid", player.getUniqueId().toString());
            entry.put("name", player.getName());
            players.put(entry);
        }
        JSONObject packet = new JSONObject();
        packet.put("type", "voice_players");
        packet.put("players", players);
        return packet;
    }
}
