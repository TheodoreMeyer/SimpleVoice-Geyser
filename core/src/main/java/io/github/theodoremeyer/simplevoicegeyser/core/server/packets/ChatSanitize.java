package io.github.theodoremeyer.simplevoicegeyser.core.server.packets;

/**
 * Shared chat sanitization for web ↔ Minecraft bridging.
 */
public final class ChatSanitize {

    public static final int MAX_CHAT_LENGTH = 200;

    /** Prefix used when a web client posts into Minecraft via {@link ChatPacket}. */
    public static final String WEB_CHAT_PREFIX = "[Web Chat]: ";

    private ChatSanitize() {}

    /**
     * Strip formatting / control characters and cap length for safe web display and forward.
     */
    public static SanitizedChat sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return new SanitizedChat("", 0, false, 0);
        }

        int originalLength = raw.length();
        StringBuilder sanitized = new StringBuilder(Math.min(originalLength, MAX_CHAT_LENGTH));
        int removedCount = 0;
        boolean truncated = false;
        boolean prevSpace = false;

        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);

            if (ch == '\r' || ch == '\n' || ch == '\t') {
                ch = ' ';
            }

            if (ch == '\u00A7' || ch < 0x20 || ch == 0x7F) {
                removedCount++;
                continue;
            }

            if (ch > 0x7E) {
                removedCount++;
                continue;
            }

            if (ch == ' ') {
                if (prevSpace) {
                    continue;
                }
                prevSpace = true;
            } else {
                prevSpace = false;
            }

            if (sanitized.length() >= MAX_CHAT_LENGTH) {
                truncated = true;
                break;
            }

            sanitized.append(ch);
        }

        String result = sanitized.toString().trim();
        return new SanitizedChat(result, originalLength, truncated, removedCount);
    }

    /**
     * If {@code message} was produced by web→game forward, return the body without the prefix; else null.
     */
    public static String stripWebChatPrefix(String message) {
        if (message == null) {
            return null;
        }
        String trimmed = message.trim();
        if (trimmed.regionMatches(true, 0, WEB_CHAT_PREFIX, 0, WEB_CHAT_PREFIX.length())) {
            return trimmed.substring(WEB_CHAT_PREFIX.length()).trim();
        }
        return null;
    }

    public record SanitizedChat(
            String sanitized,
            int originalLength,
            boolean truncated,
            int removedCount
    ) {}
}
