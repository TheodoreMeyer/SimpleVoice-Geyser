package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

/**
 * Class holding enums for different connection states.
 */
public final class ConnectionStates {

    /**
     * This class is a wrapper for connections (perhaps may be changed) and therefore needs no instantiation itself.
     */
    private ConnectionStates() {}

    /**
     * Defines the type of Message being sent to the client.
     */
    public enum MessageType {
        /**
         * Represents an error for the client
         */
        ERROR("error"),

        /**
         * Represents what is a chat message to the client
         */
        CHAT("chat"),

        /**
         * Any non-error status messages
         */
        STATUS("status"),

        /**
         * Non-specific info.
         */
        GENERIC("generic");

        private final String jsonString;

        MessageType(String jsonString) {
            this.jsonString = jsonString;
        }

        /**
         * Get the string of the type.
         * @return string
         */
        public String getJsonString() { return jsonString; }

        @Override
        public String toString() {
            return jsonString;
        }
    }

    /**
     * Defines the different disconnect codes that can be sent to the client when disconnecting them.
     */
    public enum DisconnectCodes {
        /**
         * Generic close code
         */
        GENERIC(1001),

        /**
         * Code representing a new session reconnected under same player, replacing the old session.
         */
        REPLACED(4001),

        /**
         * Code Represented on a timeout
         * Has not been completely implemented on server yet.
         */
        TIMEOUT(4002),

        /**
         * Code indicating the session's player has left the game
         */
        PLAYER_LEAVE(4003),

        /**
         * Code indicating a fatal error has occurred, meaning the websocket needs to close and reopen
         */
        FATAL_ERROR(4004),

        /**
         * Indicating a packet error has occurred, such as a malformed packet or failure to send a packet.
         */
        PACKET_ERROR(4005),

        /**
         * Code indicating the server is shutting down.
         */
        SERVER_SHUTDOWN(4006),

        /**
         * Code indicating that the session is closed, as far as the server knows.
         */
        CLOSED_SESSION(4007),

        /**
         * Code indicating client needs to update local code
         */
        OUTDATED_CLIENT(4008);

        private final int code;

        /**
         * represents a close-type
         * @param code code that will be sent to the client on disconnect
         */
        DisconnectCodes(int code) {
            this.code = code;
        }

        /**
         * Get the associated Code to the error
         * @return code
         */
        public int getCode() { return code; }

        @Override
        public String toString() {
            return String.valueOf(code);
        }
    }
}
