package io.github.theodoremeyer.simplevoicegeyser.velocity.impl.sender;

import com.velocitypowered.api.proxy.ConsoleCommandSource;
import net.kyori.adventure.text.Component;

/**
 * Thin wrapper around the Velocity console command source for sending
 * plain-text messages.
 */
public class VelocityConsole {

    private final ConsoleCommandSource source;

    /**
     * Wrap the given console source.
     *
     * @param source Velocity console command source
     */
    public VelocityConsole(ConsoleCommandSource source) {
        this.source = source;
    }

    /**
     * Get the sender name.
     * @return always {@code "Console"}
     */
    public String getName() {
        return "Console";
    }

    /**
     * Send a plain-text message to the console.
     *
     * @param message text to send
     */
    public void sendMessage(String message) {
        source.sendMessage(Component.text(message));
    }
}
