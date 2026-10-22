package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import org.json.JSONObject;

/**
 * Web client request to leave the current SVC group.
 */
public class LeaveGroupPacket implements Packet {

    public LeaveGroupPacket() {}

    @Override
    public String getType() {
        return "leave_group";
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
            socket.getConnection().sendError("You must be in Minecraft to leave a group.", false);
            return;
        }

        if (!player.hasPermission("svg.vc.group.join")
                && !player.hasPermission("svg.vc.group")) {
            socket.getConnection().sendError(
                    "You do not have permission to leave groups.",
                    false
            );
            return;
        }

        boolean ok = SvgCore.getGroupManager().leaveGroup(player);
        if (!ok) {
            socket.getConnection().sendError("Could not leave group.", false);
            GroupsBroadcaster.sendTo(socket.getConnection());
            return;
        }

        socket.getConnection().sendStatus("Left group.");
        GroupsBroadcaster.sendTo(socket.getConnection());
    }
}
