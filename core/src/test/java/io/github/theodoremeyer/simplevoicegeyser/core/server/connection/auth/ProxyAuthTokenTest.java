package io.github.theodoremeyer.simplevoicegeyser.core.server.connection.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class ProxyAuthTokenTest {
    private static final String SECRET = "test-secret";

    @Test
    void acceptsEachValidTokenOnlyOnce() {
        String token = ProxyAuthToken.create(UUID.randomUUID(), "player", SECRET, Duration.ofMinutes(1));
        assertNotNull(ProxyAuthToken.validate(token, SECRET));
        assertNull(ProxyAuthToken.validate(token, SECRET));
    }

    @Test
    void concurrentReplayHasOnlyOneWinner() throws Exception {
        String token = ProxyAuthToken.create(UUID.randomUUID(), "player", SECRET, Duration.ofMinutes(1));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> validate = () -> ProxyAuthToken.validate(token, SECRET) != null;
            Future<Boolean> first = executor.submit(validate);
            Future<Boolean> second = executor.submit(validate);
            assertNotEquals(first.get(), second.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsExpiredAndIncorrectlySignedTokensWhileAllowingDistinctTokens() {
        String expired = ProxyAuthToken.create(UUID.randomUUID(), "player", SECRET, Duration.ZERO);
        // A zero TTL is deliberately normalized to one second; wait until it is definitely expired.
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(e);
        }
        assertNull(ProxyAuthToken.validate(expired, SECRET));

        String first = ProxyAuthToken.create(UUID.randomUUID(), "one", SECRET, Duration.ofMinutes(1));
        String second = ProxyAuthToken.create(UUID.randomUUID(), "two", SECRET, Duration.ofMinutes(1));
        assertNull(ProxyAuthToken.validate(first, "wrong-secret"));
        assertNotNull(ProxyAuthToken.validate(first, SECRET));
        assertNotNull(ProxyAuthToken.validate(second, SECRET));
    }
}
