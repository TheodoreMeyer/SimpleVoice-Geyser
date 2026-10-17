package io.github.theodoremeyer.simplevoicegeyser.core.proxy;

/** Result returned by the Velocity proxy player-state endpoint. */
public record ProxyPlayerState(boolean passwordSet, boolean changingServer) {
    public static ProxyPlayerState unavailable() {
        return new ProxyPlayerState(false, false);
    }
}
