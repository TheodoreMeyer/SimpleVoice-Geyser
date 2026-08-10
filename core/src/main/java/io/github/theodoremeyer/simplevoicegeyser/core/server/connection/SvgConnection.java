package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgPlayer;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.AudioSessionNegotiation;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.SessionVoiceMembership;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.SvgAudioListener;
import io.github.theodoremeyer.simplevoicegeyser.core.audio.SvgAudioSender;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth.AuthException;
import io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility.ClientIdentity;
import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a single active websocket + voice connection.
 * <p>
 * This is the authoritative runtime state for a client.
 */
public final class SvgConnection {

    private static final AtomicLong SESSION_GENERATION = new AtomicLong();

    private final UUID uuid;
    private final Session session;
    private final SvgPlayer player;
    private final long sessionGeneration;
    private SvgAudioSender audioSender;
    private SvgAudioListener audioListener;
    private final AudioSessionNegotiation audioNegotiation;
    private final ClientIdentity clientIdentity;
    private final AtomicReference<SessionVoiceMembership> membership =
            new AtomicReference<>();
    private volatile SessionMode sessionMode = SessionMode.WEB_VOICE;
    private volatile boolean authenticated;
    private volatile boolean closed;

    /**
     * Create a Connection
     * @param session associated Session
     * @param player associated player
     * @param audioNegotiation negotiation for audio
     * @param clientIdentity the Client's Identity
     */
    SvgConnection(
            Session session,
            SvgPlayer player,
            AudioSessionNegotiation audioNegotiation,
            ClientIdentity clientIdentity
    ) {
        this.player = player;
        this.uuid = player.getUniqueId();
        this.session = session;
        this.audioNegotiation = audioNegotiation;
        this.clientIdentity = clientIdentity;
        this.sessionGeneration = SESSION_GENERATION.incrementAndGet();
        this.membership.set(SessionVoiceMembership.uncertain(uuid, sessionGeneration));
    }

    /**
     * Authenticate the session for the player
     * @throws AuthException exception if authentication fails
     */
    public synchronized void authenticate() throws AuthException {
        if (authenticated) {
            return;
        }

        VoicechatServerApi api = SvgCore.getBridge().getVcServerApi();
        if (api == null) {
            throw new AuthException("VoicechatServerApi is null");
        }

        // Re-fetch connection at auth time (player may have installed/uninstalled mid-handshake).
        VoicechatConnection connection = api.getConnectionOf(uuid);
        if (connection == null) {
            throw new AuthException("VoicechatConnection is null for: " + uuid);
        }

        if (connection.isInstalled()) {
            sessionMode = SessionMode.NATIVE_VOICE_CONTROLLER;
            audioListener = null;
            audioSender = null;
            authenticated = true;
            // Native controllers do not transmit browser audio; keep gate closed.
            applyMembership(SessionVoiceMembership.none(uuid, sessionGeneration, 0L));

            SvgCore.getLogger().debug(
                    "SvgConnection: Authenticated native voice controller: "
                            + uuid
                            + " client="
                            + clientIdentity.toLogString()
            );
            return;
        }

        sessionMode = SessionMode.WEB_VOICE;

        audioListener = new SvgAudioListener(uuid, session, api, audioNegotiation);
        if (!audioListener.registerListener()) {
            audioListener = null;
            throw new AuthException("Failed to register audio listener for: " + uuid);
        }

        audioSender = new SvgAudioSender(api, uuid, sessionGeneration);
        authenticated = true;

        // Re-fetch SVC connection for membership (do not trust a stale snapshot).
        VoicechatConnection fresh = api.getConnectionOf(uuid);
        UUID groupId = null;
        if (fresh != null && fresh.isInGroup()) {
            Group group = fresh.getGroup();
            if (group != null) {
                groupId = group.getId();
            }
        }
        long revision = 0L;
        try {
            if (SvgCore.getGroupManager() != null) {
                revision = SvgCore.getGroupManager().getRevision();
            }
        } catch (RuntimeException ignored) {
        }
        if (groupId != null) {
            applyMembership(SessionVoiceMembership.joined(uuid, sessionGeneration, groupId, revision));
        } else {
            applyMembership(SessionVoiceMembership.none(uuid, sessionGeneration, revision));
        }

        SvgCore.getLogger().debug(
                "SvgConnection: Authenticated connection: "
                        + uuid
                        + " mode="
                        + sessionMode
                        + " group="
                        + (groupId == null ? "none" : groupId)
                        + " client="
                        + clientIdentity.toLogString()
        );
    }

    /**
     * @return session generation for this websocket lifecycle
     */
    public long getSessionGeneration() {
        return sessionGeneration;
    }

    /**
     * @return current membership snapshot
     */
    public SessionVoiceMembership getVoiceMembership() {
        return membership.get();
    }

    /**
     * Apply authoritative membership to this session and its audio sender.
     * Membership from another session generation is ignored (fail-closed).
     *
     * @param next membership
     * @return true when applied
     */
    public boolean applyMembership(SessionVoiceMembership next) {
        if (next == null) {
            return false;
        }
        if (!uuid.equals(next.playerUuid()) || next.sessionGeneration() != sessionGeneration) {
            return false;
        }
        membership.set(next);
        if (audioSender != null) {
            audioSender.applyMembership(next);
        }
        return true;
    }

    /**
     * Close outbound voice immediately (leave / group removal). Clears queued frames first.
     *
     * @param membershipRevision revision
     */
    public void closeVoiceTransmit(long membershipRevision) {
        SessionVoiceMembership closedMembership =
                SessionVoiceMembership.none(uuid, sessionGeneration, membershipRevision);
        membership.set(closedMembership);
        if (audioSender != null) {
            audioSender.closeTransmit(membershipRevision);
        }
    }

    /**
     * @return whether this session may transmit browser voice
     */
    public boolean allowsVoiceTransmit() {
        if (sessionMode != SessionMode.WEB_VOICE || audioSender == null) {
            return false;
        }
        SessionVoiceMembership current = membership.get();
        return current != null && current.allowsTransmit();
    }

    /**
     * Disconnect the SvgConnection from SVC and the session
     * @param code close code
     * @param reason close reason
     */
    public synchronized void disconnect(int code, String reason) {
        if (closed) {
            return;
        }

        closed = true;
        authenticated = false;
        closeVoiceTransmit(membership.get() == null ? 0L : membership.get().membershipRevision());

        if (audioSender != null) {
            try {
                audioSender.unregister();
            } catch (Exception e) {
                SvgCore.getLogger().debug("SvgConnection: Failed to unregister audio sender", e);
            }
        }

        if (audioListener != null) {
            try {
                audioListener.unRegister();
            } catch (Exception e) {
                SvgCore.getLogger().debug("SvgConnection: Failed to unregister audio listener", e);
            }
        }

        if (session.isOpen()) {
            try {
                session.close(code, reason);
            } catch (Exception e) {
                SvgCore.getLogger().debug("SvgConnection: Failed to close websocket session", e);
            }
        }

        SvgCore.getLogger().debug("SvgConnection: Disconnected connection: " + uuid + " (" + reason + ")");

        if (player.isOnline()) {
            player.sendMessage(SvgCore.getPrefix() + "Audio Disconnected");
        }
    }

    /**
     * Send JSON data to the client
     * @param json JSON packet
     */
    public void sendJson(JSONObject json) {
        if (closed || !session.isOpen()) {
            return;
        }

        try {
            session.getRemote().sendString(json.toString());
        } catch (IOException e) {
            SvgCore.getLogger().debug("SvgConnection: Failed to send json packet", e);
            disconnect(ConnectionStates.DisconnectCodes.FATAL_ERROR.getCode(), "Packet send failure");
        }
    }

    /**
     * Send a message to the client
     * @param type message type
     * @param message message content
     * @param fatal whether the message is fatal
     */
    public void sendMessage(ConnectionStates.MessageType type, String message, boolean fatal) {
        JSONObject json = new JSONObject();
        json.put("type", type.getJsonString());
        json.put("message", message);
        json.put("fatal", fatal);
        sendJson(json);
    }

    /**
     * Send an error to the client
     * @param message error message
     * @param fatal whether the error is fatal
     */
    public void sendError(String message, boolean fatal) {
        sendMessage(ConnectionStates.MessageType.ERROR, message, fatal);
    }

    /**
     * Send a status message
     * @param message message to send
     */
    public void sendStatus(String message) {
        sendMessage(ConnectionStates.MessageType.STATUS, message, false);
    }

    /**
     * Send a Chat Message to the client
     * @param message chat message
     */
    public void sendChat(String message) {
        sendMessage(ConnectionStates.MessageType.CHAT, message, false);
    }

    /**
     * Send a fatal message to the client
     * @param message fatal message
     * @param closeCode close code
     * @param closeReason close reason
     */
    public void sendFatal(String message, int closeCode, String closeReason) {
        sendError(message, true);
        disconnect(closeCode, closeReason);
    }

    /**
     * Get the Associated player's UUID
     * @return Uuid
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Get the associated player
     * @return SvgPlayer
     */
    public SvgPlayer getPlayer() {
        return player;
    }

    /**
     * Check if the Connection has authenticated yet
     * @return if it has authenticated
     */
    public boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * Check to see if the connection has closed
     * @return is closed
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Session participation mode after authentication.
     * @return session mode
     */
    public SessionMode getSessionMode() {
        return sessionMode;
    }

    /**
     * @return true when this session owns web audio transport
     */
    public boolean isWebVoice() {
        return sessionMode == SessionMode.WEB_VOICE;
    }

    /**
     * Get the AudioSender that this Connection handles
     * @return AudioSender, or null for native voice controllers
     */
    public SvgAudioSender getAudioSender() {
        if (sessionMode == SessionMode.NATIVE_VOICE_CONTROLLER) {
            return null;
        }
        return audioSender;
    }

    /**
     * Get the Client's Identity
     * <p>
     * May be removed, and is unused
     * @return ClientIdentity
     */
    public ClientIdentity getClientIdentity() {
        return clientIdentity;
    }
}
