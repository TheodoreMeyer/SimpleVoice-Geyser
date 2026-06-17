package io.github.theodoremeyer.simplevoicegeyser.velocity.impl.sender;

import com.velocitypowered.api.proxy.ConsoleCommandSource;
import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgConsole;
import net.kyori.adventure.text.Component;

public class VelocityConsole extends SvgConsole {

    private final ConsoleCommandSource source;

    public VelocityConsole(ConsoleCommandSource source) {
        this.source = source;
    }

    @Override
    public void sendMessage(String message) {
        source.sendMessage(Component.text(message));
    }
}
