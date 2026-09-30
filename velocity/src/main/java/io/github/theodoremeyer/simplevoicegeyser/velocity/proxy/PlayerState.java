package io.github.theodoremeyer.simplevoicegeyser.velocity.proxy;

import io.github.theodoremeyer.simplevoicegeyser.velocity.VelocityPlugin;
import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONObject;

import java.util.UUID;

/** State belonging to one authenticated browser/player session. */
public final class PlayerState {
    private final VelocityPlugin plugin;
    private final UUID uuid;
    private final String username;
    private final BackendRelay relay;

    private JSONObject joinRequest;
    private JSONObject capabilitiesRequest;
    private String backendUrl;

    public PlayerState(Session clientSession, VelocityPlugin plugin, UUID uuid, String username) {
        this.plugin = plugin;
        this.uuid = uuid;
        this.username = username;
        this.relay = new BackendRelay(clientSession, plugin.getLogger());
    }

    public UUID uuid() {
        return uuid;
    }

    public BackendRelay relay() {
        return relay;
    }

    public JSONObject joinRequest() {
        return joinRequest;
    }

    public boolean isOn(String url) {
        return url != null && url.equals(backendUrl);
    }

    public void setJoinRequest(JSONObject request) {
        this.joinRequest = request;
    }

    public void setCapabilitiesRequest(JSONObject request) {
        this.capabilitiesRequest = request;
        relay.updateCapabilitiesPayload(request == null ? null : request.toString());
    }

    public void connect(String clientName, String backendUrl) {
        this.backendUrl = backendUrl;
        relay.connect(backendUrl, buildJoinPayload(clientName).toString());
    }

    /** Move the authenticated session to another backend and resend its handshake. */
    public void changeBackend(String clientName, String backendUrl) {
        if (joinRequest != null && !isOn(backendUrl)) {
            relay.updateJoinPayload(buildJoinPayload(clientName).toString());
            if (capabilitiesRequest != null) {
                relay.updateCapabilitiesPayload(capabilitiesRequest.toString());
            }
            relay.reconnect(backendUrl);
            this.backendUrl = backendUrl;
        }
    }

    public void close(int code, String reason) {
        relay.close(code, reason);
    }

    private JSONObject buildJoinPayload(String clientName) {
        JSONObject join = new JSONObject(joinRequest.toString());
        join.put("password", "");
        join.put("proxyToken", plugin.createProxyToken(uuid, username, clientName));
        return join;
    }
}
