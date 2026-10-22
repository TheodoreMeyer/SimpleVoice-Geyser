package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

import de.maxhenkel.voicechat.api.Group;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.ConnectionStates;
import io.github.theodoremeyer.simplevoicegeyser.core.server.servlets.JettyWebSocket;
import org.json.JSONObject;

/**
 * Web client request to create (and join) an SVC group.
 */
public class CreateGroupPacket implements Packet {

    private static final int MAX_NAME_LENGTH = 32;

    public CreateGroupPacket() {}

    @Override
    public String getType() {
        return "create_group";
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
            socket.getConnection().sendError("You must be in Minecraft to create a group.", false);
            return;
        }

        if (!player.hasPermission("svg.vc.group.create")) {
            socket.getConnection().sendError(
                    "You do not have permission to create groups.",
                    false
            );
            return;
        }

        String name = json.optString("name", "").trim();
        if (name.isEmpty()) {
            socket.getConnection().sendError("Group name is required.", false);
            return;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            socket.getConnection().sendError(
                    "Group name must be at most " + MAX_NAME_LENGTH + " characters.",
                    false
            );
            return;
        }

        String password = json.has("password") ? json.optString("password", "") : "";
        if (password != null && password.isEmpty()) {
            password = null;
        }

        // Default ISOLATED (group-only audio). Field is groupType — packet
        // discriminator already uses "type": "create_group".
        Group.Type groupType = Group.Type.ISOLATED;
        String typeRaw = json.optString("groupType", "").trim();
        if (!typeRaw.isEmpty()) {
            groupType = SvgCore.getGroupManager().stringToType(typeRaw);
        }

        boolean ok = SvgCore.getGroupManager().createGroup(
                player,
                name,
                password,
                groupType,
                false,
                false
        );

        if (!ok) {
            socket.getConnection().sendError(
                    "Could not create group. The name may already be taken.",
                    false
            );
            GroupsBroadcaster.sendTo(socket.getConnection());
            return;
        }

        boolean hasPassword = password != null && !password.isEmpty();
        String typeLabel = typeLabel(groupType);
        SvgCore.getLogger().info(
                "[Groups] Created group name=" + name
                        + " type=" + typeLabel
                        + " password=" + (hasPassword ? "yes" : "no")
                        + " by=" + player.getName()
        );
        socket.getConnection().sendStatus("Created group: " + name + " (" + typeLabel + ")");
        GroupsBroadcaster.sendTo(socket.getConnection());
    }

    private static String typeLabel(Group.Type type) {
        if (type == Group.Type.ISOLATED) {
            return "ISOLATED";
        }
        if (type == Group.Type.NORMAL) {
            return "NORMAL";
        }
        if (type == Group.Type.OPEN) {
            return "OPEN";
        }
        return String.valueOf(type);
    }
}
