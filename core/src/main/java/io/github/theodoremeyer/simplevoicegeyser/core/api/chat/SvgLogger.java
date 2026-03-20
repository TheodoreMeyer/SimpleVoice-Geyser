package io.github.theodoremeyer.simplevoicegeyser.core.api.chat;

/**
 * Cross-platform logging abstraction for SimpleVoice-Geyser.
 * without exposing platform-specific logging implementations.
 */
public interface SvgLogger {

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
}