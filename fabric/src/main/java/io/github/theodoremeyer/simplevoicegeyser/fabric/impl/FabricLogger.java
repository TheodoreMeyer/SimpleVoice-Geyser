package io.github.theodoremeyer.simplevoicegeyser.fabric.impl;

import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FabricLogger implements SvgLogger {

    private final Logger logger = LoggerFactory.getLogger("SimpleVoiceGeyser");

    @Override
    public void info(String msg) {
        logger.info(msg);
    }

    @Override
    public void warning(String msg) {
        logger.warn(msg);
    }

    @Override
    public void error(String msg) {
        logger.error(msg);
    }

    @Override
    public void severe(String msg) {
        logger.error(msg); // SLF4J has no severe → map to error
    }

    @Override
    public void error(String msg, Throwable t) {
        logger.error(msg, t);
    }
}