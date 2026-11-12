package io.github.theodoremeyer.simplevoicegeyser.velocity.proxy;

/**
 * Constants describing proxy connection messaging and disconnect behavior.
 */
public final class ConnectionStates {

    private ConnectionStates() {}

    /**
     * Types of JSON control messages the proxy sends to the browser client.
     */
    public enum MessageType {
        /** An error occurred; the message field carries a description. */
        ERROR("error"),
        /** A chat message. */
        CHAT("chat"),
        /** A status update about the current connection. */
        STATUS("status"),
        /** A message with no more specific type. */
        GENERIC("generic");

        private final String jsonString;

        MessageType(String jsonString) {
            this.jsonString = jsonString;
        }

        /**
         * Get the wire-format string used as the {@code type} value in JSON messages.
         * @return the lowercase wire-format name of this type
         */
        public String getJsonString() { return jsonString; }

        @Override
        public String toString() {
            return jsonString;
        }
    }

    /**
     * WebSocket close codes used when the proxy terminates a browser session.
     * Values 4000+ are application-defined.
     */
    public enum DisconnectCodes {
        /** Unspecified closure. */
        GENERIC(1001),
        /** The session was replaced by a newer one for the same player. */
        REPLACED(4001),
        /** The session timed out. */
        TIMEOUT(4002),
        /** The player left the network. */
        PLAYER_LEAVE(4003),
        /** An unrecoverable error ended the session. */
        FATAL_ERROR(4004),
        /** A malformed packet ended the session. */
        PACKET_ERROR(4005),
        /** The backend or proxy is shutting down. */
        SERVER_SHUTDOWN(4006),
        /** The session was closed administratively. */
        CLOSED_SESSION(4007),
        /** The web client build is incompatible with this proxy. */
        OUTDATED_CLIENT(4008);

        private final int code;

        DisconnectCodes(int code) {
            this.code = code;
        }

        /**
         * Get the numeric WebSocket close code for this condition.
         * @return the close code
         */
        public int getCode() { return code; }

        @Override
        public String toString() {
            return String.valueOf(code);
        }
    }
}
