package io.github.theodoremeyer.simplevoicegeyser.core.proxy;

/**
 * Result returned by the Velocity proxy player-state endpoint.
 * @param passwordSet whether the player has a password set
 * @param changingServer whether the player is currently changing servers
 */
public record ProxyPlayerState(boolean passwordSet, boolean changingServer) {

    /**
     * Returns a ProxyPlayerState instance representing an unavailable state.
     * @return a ProxyPlayerState instance representing an unavailable state
     */
    public static ProxyPlayerState unavailable() {
        return new ProxyPlayerState(false, false);
    }
}
