package io.github.theodoremeyer.simplevoicegeyser.spigotmc.impl;

import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;

import java.util.logging.Logger;

public class BukkitLogger implements SvgLogger {

    private final Logger logger;

    public BukkitLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String msg) {
        logger.info(msg);
    }

    @Override
    public void warning(String msg) {
        logger.warning(msg);
    }

    @Override
    public void error(String msg) {
        logger.severe(msg); // JUL has no error → severe
    }

    @Override
    public void severe(String msg) {
        logger.severe(msg);
    }

    @Override
    public void error(String msg, Throwable t) {
        logger.severe(msg);
        t.printStackTrace();
    }
}