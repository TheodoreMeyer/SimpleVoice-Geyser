package io.github.theodoremeyer.simplevoicegeyser.core.audio;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioThreadLifecycleTest {

    @Test
    void shutdownRejectsNewWorkIdempotently() throws Exception {
        new AudioThread();
        CountDownLatch ran = new CountDownLatch(1);
        AudioThread.execute(ran::countDown);
        assertTrue(ran.await(2, TimeUnit.SECONDS));

        AudioThread.shutdown();
        AudioThread.shutdown();

        assertFalse(AudioThread.isAccepting());
        AtomicBoolean afterShutdown = new AtomicBoolean(false);
        AudioThread.execute(() -> afterShutdown.set(true));
        Thread.sleep(100);
        assertFalse(afterShutdown.get());
    }
}
