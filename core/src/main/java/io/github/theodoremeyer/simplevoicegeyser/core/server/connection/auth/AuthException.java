package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth;

/**
 * Exception for handling Authentication problems
 */
public class AuthException extends IllegalStateException {

    /**
     * create the exception
     * @param message message
     */
    public AuthException(String message) {
        super(message);
    }
}
