package io.github.theodoremeyer.simplevoicegeyser.core.server.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketTest {

    private TokenBucket bucket;

    @BeforeEach
    void setUp() {
        bucket = new TokenBucket(4, 2);
    }

    @Test
    void allowsBurstThenLimits() {
        assertEquals(0L, bucket.tryConsume(1));
        assertEquals(0L, bucket.tryConsume(1));
        assertEquals(0L, bucket.tryConsume(1));
        assertEquals(0L, bucket.tryConsume(1));
        assertTrue(bucket.tryConsume(1) > 0L);
    }
}
