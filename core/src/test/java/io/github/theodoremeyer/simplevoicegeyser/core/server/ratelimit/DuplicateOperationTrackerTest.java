package io.github.theodoremeyer.simplevoicegeyser.core.server.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateOperationTrackerTest {

    @Test
    void rejectsDuplicateOperationIdsWithinRetention() {
        DuplicateOperationTracker tracker = new DuplicateOperationTracker(120);
        assertTrue(tracker.register("conn-1", "op-a"));
        assertFalse(tracker.register("conn-1", "op-a"));
        assertTrue(tracker.register("conn-1", "op-b"));
        assertTrue(tracker.register("conn-2", "op-a"));
    }
}
