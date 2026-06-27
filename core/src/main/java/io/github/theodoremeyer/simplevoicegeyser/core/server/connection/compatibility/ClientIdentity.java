package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility;

public record ClientIdentity(String kind, String version, int protocol) {

    private static final ClientIdentity BROWSER = new ClientIdentity("browser", "", 0);

    public static ClientIdentity browser() {
        return BROWSER;
    }

    /**
     * Log-safe identity summary. Do not log the full join packet.
     */
    public String toLogString() {
        if (this.equals(BROWSER)) {
            return "browser";
        }

        return kind + " version=" + sanitizeForLog(version) + " protocol=" + protocol;
    }

    private static String sanitizeForLog(String value) {
        StringBuilder sanitized = new StringBuilder(value.length());

        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            sanitized.append(ch < 0x20 || ch == 0x7F ? '?' : ch);
        }

        return sanitized.toString();
    }
}
