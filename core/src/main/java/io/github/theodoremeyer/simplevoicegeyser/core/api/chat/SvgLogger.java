package io.github.theodoremeyer.simplevoicegeyser.core.api.chat;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cross-platform logging abstraction for SimpleVoice-Geyser.
 * without exposing platform-specific logging implementations.
 */
public interface SvgLogger {

    /**
     * Whether Debug is enabled
     */
    AtomicBoolean debug  = new AtomicBoolean();

    /**
     * Logs an informational message.
     *
     * @param msg the message to log
     */
    void info(String msg);

    /**
     * Logs a warning message.
     *
     * @param msg the warning message to log
     */
    void warning(String msg);

    /**
     * Logs an error message.
     *
     * @param msg the error message to log
     */
    void error(String msg);

    /**
     * Logs a severe (critical/fatal) message.
     *
     * <p>On SLF4J platforms (Fabric), this is typically mapped to {@code error}.
     *
     * @param msg the severe message to log
     */
    void severe(String msg);

    /**
     * Logs an error message with an associated throwable.
     *
     * <p>Implementations should ensure stack traces are properly printed
     * using the platform's native logging system.
     *
     * @param msg the error message to log
     * @param t the throwable associated with the error
     */
    void error(String msg, Throwable t);

    /**
     * Updated debug to true or false.
     * @param enabled whether debug is enabled
     */
    default void setDebug(boolean enabled) {
        debug.set(enabled);
    }

    /**
     * Logs a debug message to logger
     * @param msg the debug message to log
     */
    default void debug(String msg) {
        if (debug.get()) {
            info("[DEBUG] " + msg);
        }
    }

    /**
     * Logs a debug message + error to logger
     * @param msg the debug message to log
     * @param t the throwable associated with the error
     */
    default void debug(String msg, Throwable t) {
        if (debug.get()) {
            error("[DEBUG] " + msg, t);
        }
    }
}