package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility;

public record ClientIdentity(String type, String serverVersion, String serverBuild) {

    public static ClientIdentity web(String serverVersion, String serverBuild) {
        return new ClientIdentity("Web", serverVersion, serverBuild);
    }

    public static ClientIdentity svgApp(String serverVersion, String serverBuild) {
        return new ClientIdentity("Svg-App", serverVersion, serverBuild);
    }

    public String toLogString() {
        StringBuilder summary = new StringBuilder(type)
                .append(" serverVersion=")
                .append(sanitizeForLog(serverVersion));

        if (serverBuild != null && !serverBuild.isBlank()) {
            summary.append(" serverBuild=").append(sanitizeForLog(serverBuild));
        }

        return summary.toString();
    }

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
