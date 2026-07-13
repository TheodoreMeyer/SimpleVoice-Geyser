package io.github.theodoremeyer.simplevoicegeyser.velocity.proxy;

public final class ConnectionStates {

    private ConnectionStates() {}

    public enum MessageType {
        ERROR("error"),
        CHAT("chat"),
        STATUS("status"),
        GENERIC("generic");

        private final String jsonString;

        MessageType(String jsonString) {
            this.jsonString = jsonString;
        }

        public String getJsonString() { return jsonString; }

        @Override
        public String toString() {
            return jsonString;
        }
    }

    public enum DisconnectCodes {
        GENERIC(1001),
        REPLACED(4001),
        TIMEOUT(4002),
        PLAYER_LEAVE(4003),
        FATAL_ERROR(4004),
        PACKET_ERROR(4005),
        SERVER_SHUTDOWN(4006),
        CLOSED_SESSION(4007),
        OUTDATED_CLIENT(4008);

        private final int code;

        DisconnectCodes(int code) {
            this.code = code;
        }

        public int getCode() { return code; }

        @Override
        public String toString() {
            return String.valueOf(code);
        }
    }
}
