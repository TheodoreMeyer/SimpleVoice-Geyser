package io.github.theodoremeyer.simplevoicegeyser.core.server.connection;

public class ConnectionStates {
    public enum MessageType {
        ERROR("error"),
        CHAT("chat"),
        STATUS("status"),;

        private String jsonString;

        MessageType(String jsonString) {
            this.jsonString = jsonString;
        }

        public String getJsonString() { return jsonString; }

        @Override
        public String toString() {
            return jsonString;
        }
    }
}
