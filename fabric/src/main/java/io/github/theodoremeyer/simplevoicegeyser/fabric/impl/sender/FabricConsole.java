package io.github.theodoremeyer.simplevoicegeyser.fabric.impl.sender;

import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgConsole;
import io.github.theodoremeyer.simplevoicegeyser.fabric.impl.FabricLogger;

public class FabricConsole extends SvgConsole {

    private final FabricLogger logger;

    public FabricConsole(FabricLogger logger) {
        this.logger = logger;
    }

    @Override
    public void sendMessage(String message) {
        logger.info(message);
    }
}