package io.github.theodoremeyer.simplevoicegeyser.fabric.impl;

import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgColor;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FabricLogger implements SvgLogger {

    private final Logger logger = LoggerFactory.getLogger("SV-G");

    @Override
    public void info(String msg) {
        logger.info(SvgColor.strip(msg));
    }

    @Override
    public void warning(String msg) {
        logger.warn(SvgColor.strip(msg));
    }

    @Override
    public void error(String msg) {
        logger.error(SvgColor.strip(msg));
    }

    @Override
    public void severe(String msg) {
        logger.error(SvgColor.strip(msg)); // SLF4J has no severe → map to error
    }

    @Override
    public void error(String msg, Throwable t) {
        logger.error(SvgColor.strip(msg), t);
    }
}