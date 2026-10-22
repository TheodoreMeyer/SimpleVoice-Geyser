package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import org.json.JSONObject;

/**
 * Web client request to join an existing SVC group.
 */
public class JoinGroupPacket implements Packet {

    public JoinGroupPacket() {}

    @Override
    public String getType() {
        return "join_group";
    }

    @Override
    public void handle(JettyWebSocket socket, JSONObject json) {
        if (socket.getConnection() == null || !socket.getConnection().isAuthenticated()) {
            socket.sendRaw(
                    ConnectionStates.MessageType.ERROR,
                    "Access Denied: Not authenticated.",
                    false
            );
            return;
        }

        SvgPlayer player = socket.getConnection().getPlayer();
        if (player == null) {
            socket.getConnection().sendError("You must be in Minecraft to join a group.", false);
            return;
        }

        String name = json.optString("name", "").trim();
        if (name.isEmpty()) {
            socket.getConnection().sendError("Group name is required.", false);
            return;
        }

        String password = json.has("password") ? json.optString("password", "") : "";
        if (password != null && password.isEmpty()) {
            password = null;
        }

        boolean ok = SvgCore.getGroupManager().joinGroup(player, name, password);
        if (!ok) {
            socket.getConnection().sendError(
                    "Could not join group. Check the name and password.",
                    false
            );
            GroupsBroadcaster.sendTo(socket.getConnection());
            return;
        }

        socket.getConnection().sendStatus("Joined group: " + name);
        // joinGroup already broadcasts; ensure this client has a fresh list
        GroupsBroadcaster.sendTo(socket.getConnection());
    }
}
