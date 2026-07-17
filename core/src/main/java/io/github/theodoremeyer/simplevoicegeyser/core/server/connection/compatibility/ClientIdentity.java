package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility;

/**
 * Represents any known info about the client on join
 * @param type client type (like web, or App)
 * @param serverVersion client's targeted server version
 * @param serverBuild client's targeted server build
 */
public record ClientIdentity(String type, String serverVersion, String serverBuild) {

    /**
     * Easy way to create a web client identity
     * @param serverVersion targeted server version
     * @param serverBuild targeted server build
     * @return Identity
     */
    public static ClientIdentity web(String serverVersion, String serverBuild) {
        return new ClientIdentity("Web", serverVersion, serverBuild);
    }

    /**
     * Easy way to create an App client identity
     * @param serverVersion targeted server version
     * @param serverBuild targeted server build
     * @return Identity
     */
    public static ClientIdentity svgApp(String serverVersion, String serverBuild) {
        return new ClientIdentity("Svg-App", serverVersion, serverBuild);
    }

    /**
     * Log a client Identity easily
     * @return Identity as a string
     */
    public String toLogString() {
        StringBuilder summary = new StringBuilder(type)
                .append(" serverVersion=")
                .append(sanitizeForLog(serverVersion));

        if (serverBuild != null && !serverBuild.isBlank()) {
            summary.append(" serverBuild=").append(sanitizeForLog(serverBuild));
        }

        return summary.toString();
    }

    /**
     * Sanitize the value for logging by replacing control characters with a question mark.
     * @param value String to sanitize
     * @return Sanitized String
     */
    private static String sanitizeForLog(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder sanitized = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            sanitized.append(ch < 0x20 || ch == 0x7F ? '?' : ch);
        }
        return sanitized.toString();
    }
}
