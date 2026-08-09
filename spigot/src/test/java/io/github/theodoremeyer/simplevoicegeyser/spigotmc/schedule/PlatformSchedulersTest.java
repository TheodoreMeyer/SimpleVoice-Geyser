package io.github.theodoremeyer.simplevoicegeyser.spigotmc.schedule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformSchedulersTest {

    @Test
    void detectsMissingRegionSchedulersOnClassicServerDouble() {
        assertFalse(PlatformSchedulers.hasRegionSchedulers(new ClassicServerDouble()));
    }

    @Test
    void detectsRegionSchedulersWhenMethodsPresent() {
        assertTrue(PlatformSchedulers.hasRegionSchedulers(new RegionSchedulerServerDouble()));
    }

    @SuppressWarnings("unused")
    private static final class ClassicServerDouble {
        public String getName() {
            return "CraftServer";
        }
    }

    @SuppressWarnings("unused")
    private static final class RegionSchedulerServerDouble {
        public Object getGlobalRegionScheduler() {
            return new Object();
        }

        public Object getRegionScheduler() {
            return new Object();
        }

        public Object getAsyncScheduler() {
            return new Object();
        }
    }
}
