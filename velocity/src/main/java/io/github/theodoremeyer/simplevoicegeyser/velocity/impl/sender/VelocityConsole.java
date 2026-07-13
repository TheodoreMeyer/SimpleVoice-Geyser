package io.github.theodoremeyer.simplevoicegeyser.velocity.impl.sender;

import com.velocitypowered.api.proxy.ConsoleCommandSource;
import net.kyori.adventure.text.Component;

public class VelocityConsole {

    private final ConsoleCommandSource source;

    public VelocityConsole(ConsoleCommandSource source) {
        this.source = source;
    }

    public String getName() {
        return "Console";
    }

    public void sendMessage(String message) {
        source.sendMessage(Component.text(message));
    }
}
