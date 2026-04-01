package io.github.theodoremeyer.simplevoicegeyser.fabric.impl.sender;

import io.github.theodoremeyer.simplevoicegeyser.core.api.sender.SvgConsole;

import java.util.logging.Logger;

public class FabricConsole extends SvgConsole {

    private final Logger logger;

    public FabricConsole() {
        this.logger = Logger.getLogger("SVG");
    }

    @Override
    public void sendMessage(String message) {
        // Preferred: server log output
        logger.info("[SVG-CONSOLE] " + message);
    }
}