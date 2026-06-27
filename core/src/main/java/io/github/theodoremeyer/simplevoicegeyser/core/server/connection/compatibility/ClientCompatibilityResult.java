package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.compatibility;

public record ClientCompatibilityResult(
        boolean accepted,
        ClientIdentity identity,
        String message,
        int closeCode,
        String closeReason
) {

    public static ClientCompatibilityResult accepted(ClientIdentity identity) {
        return new ClientCompatibilityResult(true, identity, "", 0, "");
    }

    public static ClientCompatibilityResult rejected(String message, int closeCode, String closeReason) {
        return new ClientCompatibilityResult(false, null, message, closeCode, closeReason);
    }
}
