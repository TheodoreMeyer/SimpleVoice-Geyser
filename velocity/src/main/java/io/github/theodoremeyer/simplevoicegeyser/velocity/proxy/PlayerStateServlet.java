package io.github.theodoremeyer.simplevoicegeyser.velocity.proxy;

import io.github.theodoremeyer.simplevoicegeyser.velocity.VelocityPlugin;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;

/** Receives player lifecycle callbacks from backend SVG instances. */
public final class PlayerStateServlet extends HttpServlet {
    private final VelocityPlugin plugin;

    public PlayerStateServlet(VelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!plugin.isValidControlSecret(request.getHeader("X-SVG-Proxy-Secret"))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        try {
            JSONObject payload = new JSONObject(request.getReader().lines().reduce("", (a, b) -> a + b));
            UUID uuid = UUID.fromString(payload.getString("uuid"));
            String username = payload.getString("username");
            boolean joined = payload.getBoolean("joined");
            JSONObject result = plugin.updatePlayerState(uuid, username, joined);
            response.setContentType("application/json");
            response.getWriter().write(result.toString());
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }
}
