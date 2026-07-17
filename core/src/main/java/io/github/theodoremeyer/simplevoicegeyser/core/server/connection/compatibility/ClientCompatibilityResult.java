package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility;

/**
 * record for handling a Compatibility check result
 * @param accepted whether its accepted
 * @param identity the client's Identity
 * @param message any message from handshake
 * @param closeCode A close Code if needed
 * @param closeReason the reason for closure if failed
 */
public record ClientCompatibilityResult(
        boolean accepted,
        ClientIdentity identity,
        String message,
        int closeCode,
        String closeReason
) {

    /**
     * Create an Accepted result
     * @param identity the client's Identity
     * @return accepted result
     */
    public static ClientCompatibilityResult accepted(ClientIdentity identity) {
        return new ClientCompatibilityResult(true, identity, "", 0, "");
    }

    /**
     * Create a Rejected result
     * @param message failure message
     * @param closeCode close code
     * @param closeReason close reason
     * @return rejected result
     */
    public static ClientCompatibilityResult rejected(String message, int closeCode, String closeReason) {
        return new ClientCompatibilityResult(false, null, message, closeCode, closeReason);
    }
}
