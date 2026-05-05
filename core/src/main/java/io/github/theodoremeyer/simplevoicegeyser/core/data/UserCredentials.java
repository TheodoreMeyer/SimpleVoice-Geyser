package io.github.theodoremeyer.simplevoicegeyser.core.data;

/**
 * Stored user account data.
 */
final class UserCredentials {

    String username;
    String passwordHash;

    UserCredentials() {}

    UserCredentials(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }
}