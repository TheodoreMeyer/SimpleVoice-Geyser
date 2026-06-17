package io.github.theodoremeyer.simplevoicegeyser.velocity;

import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import org.slf4j.Logger;

public class VelocityLogger implements SvgLogger {

    private final Logger logger;

    public VelocityLogger(Logger logger) {
        this.logger = logger;
    }

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
        logger.error(msg);
    }

    @Override
    public void error(String msg, Throwable t) {
        logger.error(msg, t);
    }
}
