package io.github.theodoremeyer.simplevoicegeyser.core.api.sender;

/**
 * Represents someone who can send a command, across multiple platforms
 */
public abstract class Sender {

    /**
     * Send a message
     * @param message message to send
     */
    public abstract void sendMessage(String message);

    /**
     * Get the Sender's Name.
     * @return the name
     */
    public abstract String getName();
}
