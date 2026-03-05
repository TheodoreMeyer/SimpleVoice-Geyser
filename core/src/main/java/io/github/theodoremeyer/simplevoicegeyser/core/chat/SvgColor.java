package io.github.theodoremeyer.simplevoicegeyser.core.chat;

/**
 * Cross-platform color & formatting enum using legacy § codes.
 * Works on all Minecraft platforms.
 */
public enum SvgColor {

    // Colors
    BLACK("§0"),
    DARK_BLUE("§1"),
    DARK_GREEN("§2"),
    DARK_AQUA("§3"),
    DARK_RED("§4"),
    DARK_PURPLE("§5"),
    GOLD("§6"),
    GRAY("§7"),
    DARK_GRAY("§8"),
    BLUE("§9"),
    GREEN("§a"),
    AQUA("§b"),
    RED("§c"),
    LIGHT_PURPLE("§d"),
    YELLOW("§e"),
    WHITE("§f"),

    // Formatting
    OBFUSCATED("§k"),
    BOLD("§l"),
    STRIKETHROUGH("§m"),
    UNDERLINE("§n"),
    ITALIC("§o"),

    // Reset
    RESET("§r");

    private final String code;

    SvgColor(String code) {
        this.code = code;
    }

    /**
     * Get the raw color code
     */
    public String code() {
        return code;
    }

    public static String translateAltColorCodes(char altColorChar, String text) {
        char[] chars = text.toCharArray();

        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == altColorChar &&
                    isColorCode(chars[i + 1])) {

                chars[i] = '§';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }

        return new String(chars);
    }

    private static boolean isColorCode(char c) {
        return "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(c) != -1;
    }

    /**
     * Apply the color to text and reset after wards
     */
    public String apply(String text) {
        return code + text + RESET.code;
    }

    /**
     * Apply without reset (useful for chaining)
     */
    public String applyNoReset(String text) {
        return code + text;
    }

    /**
     * Strip color codes from a string
     */
    public static String strip(String input) {
        return input.replaceAll("§[0-9a-fk-or]", "");
    }
}