package io.github.theodoremeyer.simplevoicegeyser.core.api.sender;

/**
 * Represents the Console across multiple platforms.
 */
public abstract class SvgConsole extends Sender {

    /**
     * Just give the Consoles name
     * @return Console
     */
    @Override
    public String getName() {
        return "Console";
    }
}
