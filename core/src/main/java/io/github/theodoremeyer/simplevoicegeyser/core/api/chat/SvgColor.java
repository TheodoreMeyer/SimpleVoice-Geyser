package io.github.theodoremeyer.simplevoicegeyser.core.api.chat;

/**
 * Cross-platform color and formatting enum using legacy § codes.
 * Works on all Minecraft platforms.
 */
public enum SvgColor {

    // Colors
    /**
     * Represents the Color Black
     */
    BLACK("§0"),

    /**
     * Represents the Color Dark Blue
     */
    DARK_BLUE("§1"),

    /**
     * Represents the Color Dark Green
     */
    DARK_GREEN("§2"),

    /**
     * Represents the Color Dark Aqua
     */
    DARK_AQUA("§3"),

    /**
     * Represents the Color Dark Red
     */
    DARK_RED("§4"),

    /**
     * Represents the Color Dark Purple
     */
    DARK_PURPLE("§5"),

    /**
     * Represents the Color Gold
     */
    GOLD("§6"),

    /**
     * Represents the Color Gray
     */
    GRAY("§7"),

    /**
     * Represents the Color Dark Gray
     */
    DARK_GRAY("§8"),

    /**
     * Represents the Color Blue
     */
    BLUE("§9"),

    /**
     * Represents the Color Green
     */
    GREEN("§a"),

    /**
     * Represents the Color Aqua
     */
    AQUA("§b"),

    /**
     * Represents the Color Red
     */
    RED("§c"),

    /**
     * Represents the Color Light Purple
     */
    LIGHT_PURPLE("§d"),

    /**
     * Represents the Color Yellow
     */
    YELLOW("§e"),

    /**
     * Represents the Color White
     */
    WHITE("§f"),

    // Formatting
    /**
     * Represents Obfuscated formatting
     */
    OBFUSCATED("§k"),

    /**
     * Represents Bold formatting
     */
    BOLD("§l"),

    /**
     * puts a StrikeThrough
     */
    STRIKETHROUGH("§m"),

    /**
     * Underlines the text
     */
    UNDERLINE("§n"),
    /**
     * Italicizes the text
     */
    ITALIC("§o"),

    /**
     * Resets the color
     */
    RESET("§r");

    /**
     * The color code
     */
    private final String code;

    /**
     * Creates an enum for a color
     * @param code the color code
     */
    SvgColor(String code) {
        this.code = code;
    }

    /**
     * Get the raw color code
     * @return the code
     */
    public String code() {
        return code;
    }

    /**
     * Translates an alternate character to the Color Code
     * @param altColorChar the character to translate from
     * @param text the text to translate
     * @return translated text
     */
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

    /**
     * Checks if the char is a Color Code
     * @param c the character to check
     * @return whether it is a color code
     */
    private static boolean isColorCode(char c) {
        return "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(c) != -1;
    }

    /**
     * Apply the color to text and reset after words
     * @param text the text to apply to
     * @return string with the code
     */
    public String apply(String text) {
        return code + text + RESET.code;
    }

    /**
     * Apply without reset (useful for chaining)
     * @param text the text to apply
     * @return the text with no reset
     */
    public String applyNoReset(String text) {
        return code + text;
    }

    /**
     * Strip color codes from a string
     * @param input the text to strip
     * @return the text with no color codes
     */
    public static String strip(String input) {
        return input.replaceAll("§[0-9a-fk-or]", "");
    }
}