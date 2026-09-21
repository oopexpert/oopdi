package de.oopexpert.oopdi;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.oopdi.exception.ContainerShutdown;
import de.oopexpert.teststructure.ClassA;
import de.oopexpert.teststructure.ClassFailingPostConstruct;
import de.oopexpert.teststructure.ClassFailingPreDestroy;
import de.oopexpert.teststructure.ClassWithPreDestroy;

/**
 * Verifies container shutdown with state handling (the counterpart of background metadata
 * warmup): best-effort destruction continues past individual {@code @PreDestroy} failures and
 * aggregates them, shutdown is idempotent, no new beans are created once shutdown has started,
 * and failed post-processing leaves no half-initialized instance behind in the cache.
 */
class TestShutdownLifecycle {

    @Test
    void testShutdownStatusTransitions() {
        OOPDI<ClassWithPreDestroy> oopdi = new OOPDI<>(ClassWithPreDestroy.class);

        Assertions.assertEquals(ShutdownStatus.ACTIVE, oopdi.getShutdownStatus());

        oopdi.getInstance(ClassWithPreDestroy.class).isDestroyed();
        oopdi.shutdown();

        Assertions.assertEquals(ShutdownStatus.SHUTDOWN, oopdi.getShutdownStatus());
    }

    @Test
    void testShutdownBeforeFirstUseIsNotSilentlyLost() {
        OOPDI<ClassA> oopdi = new OOPDI<>(ClassA.class);

        oopdi.shutdown();

        Assertions.assertEquals(ShutdownStatus.SHUTDOWN, oopdi.getShutdownStatus(),
            "Shutdown with nothing to destroy still counts as shut down");
        Assertions.assertThrows(ContainerShutdown.class, () -> oopdi.getInstance(ClassA.class),
            "Beans created after shutdown was requested must fail fast, even if no Context existed yet");
    }

    @Test
    void testFailingPreDestroyDoesNotAbortShutdownOfRemainingInstances() {
        OOPDI<ClassWithPreDestroy> oopdi = new OOPDI<>(ClassWithPreDestroy.class);

        // Creation order determines destruction order (reverse): the well-behaved bean is
        // created first so that the failing one is destroyed first.
        ClassWithPreDestroy good = oopdi.getInstance(ClassWithPreDestroy.class);
        ClassFailingPreDestroy failing = oopdi.getInstance(ClassFailingPreDestroy.class);
        good.isDestroyed();
        failing.ping();
        ClassFailingPreDestroy.preDestroyCallCount.set(0);

        RuntimeException ex = Assertions.assertThrows(RuntimeException.class, oopdi::shutdown,
            "Shutdown with a failing @PreDestroy must still report the failure");

        Assertions.assertEquals(1, ClassFailingPreDestroy.preDestroyCallCount.get(),
            "Failing @PreDestroy must have been invoked exactly once");
        Assertions.assertTrue(good.isDestroyed(),
            "Remaining instances must still be destroyed best-effort after a @PreDestroy failure");
        Assertions.assertFalse(ex.getSuppressed().length == 0,
            "Individual @PreDestroy failures must be aggregated as suppressed exceptions");
        Assertions.assertEquals(ShutdownStatus.FAILED, oopdi.getShutdownStatus());
    }

    @Test
    void testShutdownIsIdempotent() {
        OOPDI<ClassFailingPreDestroy> oopdi = new OOPDI<>(ClassFailingPreDestroy.class);

        ClassFailingPreDestroy failing = oopdi.getInstance(ClassFailingPreDestroy.class);
        failing.ping();
        ClassFailingPreDestroy.preDestroyCallCount.set(0);

        Assertions.assertThrows(RuntimeException.class, oopdi::shutdown);
        Assertions.assertEquals(1, ClassFailingPreDestroy.preDestroyCallCount.get());

        Assertions.assertDoesNotThrow(oopdi::shutdown,
            "A second shutdown must be a no-op returning the terminal status");
        Assertions.assertEquals(1, ClassFailingPreDestroy.preDestroyCallCount.get(),
            "A second shutdown must not re-run @PreDestroy methods");
    }

    @Test
    void testGetInstanceAfterShutdownFailsFast() {
        OOPDI<ClassWithPreDestroy> oopdi = new OOPDI<>(ClassWithPreDestroy.class);

        oopdi.getInstance(ClassWithPreDestroy.class).isDestroyed();
        oopdi.shutdown();

        // The guard sits on real-object creation (the single funnel in getOrCreate): proxy
        // issuance itself stays possible, but the first method call on a not-yet-resolved bean
        // fails fast instead of producing an instance that could never be destroyed again.
        // (Already-resolved beans keep working from their scope cache.)
        Assertions.assertTrue(oopdi.getInstance(ClassWithPreDestroy.class).isDestroyed(),
            "Already-resolved beans remain readable from the scope cache after shutdown");
        Assertions.assertThrows(ContainerShutdown.class,
            () -> oopdi.getInstance(ClassFailingPreDestroy.class).ping(),
            "No new beans may be created once shutdown has started");
        Assertions.assertEquals(ShutdownStatus.SHUTDOWN, oopdi.getShutdownStatus());
    }

    @Test
    void testFailedPostConstructLeavesNothingBehindInCache() {
        OOPDI<ClassFailingPostConstruct> oopdi = new OOPDI<>(ClassFailingPostConstruct.class);

        // Baseline after setup: proxy creation itself runs the superclass constructor once,
        // so only the delta per request is meaningful (not the absolute count).
        oopdi.getInstance(ClassFailingPostConstruct.class);
        int baseline = ClassFailingPostConstruct.constructorCallCount.get();

        Assertions.assertThrows(RuntimeException.class, () -> oopdi.getInstance(ClassFailingPostConstruct.class).ping(),
            "Failing @PostConstruct must propagate");

        Assertions.assertThrows(RuntimeException.class, () -> oopdi.getInstance(ClassFailingPostConstruct.class).ping(),
            "A failed bean must not stay behind half-initialized in the cache");

        Assertions.assertEquals(baseline + 2, ClassFailingPostConstruct.constructorCallCount.get(),
            "Every new request after a failed post-processing must rebuild instead of reusing a broken cached object");

        Assertions.assertDoesNotThrow(oopdi::shutdown,
            "Shutdown must tolerate failed beans that left nothing behind");
    }
}
