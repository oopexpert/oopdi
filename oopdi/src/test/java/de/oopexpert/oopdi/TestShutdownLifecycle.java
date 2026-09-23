package de.oopexpert.oopdi;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.oopdi.exception.ContainerShutdown;
import de.oopexpert.oopdi.exception.DestructionFailed;
import de.oopexpert.oopdi.metadata.MetadataMode;
import de.oopexpert.oopdi.metadata.MetadataRepository;
import de.oopexpert.oopdi.proxy.RequestScopeManager;
import de.oopexpert.oopdi.resolver.DependencyResolutionContext;
import de.oopexpert.teststructure.ClassA;
import de.oopexpert.teststructure.ClassFailingPostConstruct;
import de.oopexpert.teststructure.ClassFailingPostConstructError;
import de.oopexpert.teststructure.ClassFailingPreDestroy;
import de.oopexpert.teststructure.ClassSlowConstruction;
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

        RuntimeException ex = Assertions.assertThrows(DestructionFailed.class, oopdi::shutdown,
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

        Assertions.assertThrows(DestructionFailed.class, oopdi::shutdown);
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

    @Test
    void testBeanFinishingConstructionDuringShutdownIsDestroyedImmediately() throws InterruptedException {
        OOPDI<ClassSlowConstruction> oopdi = new OOPDI<>(ClassSlowConstruction.class);
        ClassSlowConstruction proxy = oopdi.getInstance(ClassSlowConstruction.class);

        ClassSlowConstruction.enteredConstructor = new CountDownLatch(1);
        ClassSlowConstruction.releaseConstructor = new CountDownLatch(1);
        ClassSlowConstruction.preDestroyCallCount.set(0);
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            try {
                proxy.ping();
            } catch (Throwable t) {
                workerFailure.compareAndSet(null, t);
            }
        });
        worker.start();

        // The worker is now blocked inside the constructor, i.e. past the entry guard.
        Assertions.assertTrue(ClassSlowConstruction.enteredConstructor.await(5, TimeUnit.SECONDS),
            "Worker must reach the constructor");
        oopdi.shutdown();
        ClassSlowConstruction.releaseConstructor.countDown();
        worker.join(5000);

        Assertions.assertTrue(workerFailure.get() instanceof ContainerShutdown,
            "Finishing construction during shutdown must fail fast, but was: " + workerFailure.get());
        Assertions.assertEquals(1, ClassSlowConstruction.preDestroyCallCount.get(),
            "The late-finishing instance must be destroyed immediately instead of cached without @PreDestroy");
        Assertions.assertThrows(ContainerShutdown.class, () -> oopdi.getInstance(ClassSlowConstruction.class).ping(),
            "Nothing may have been cached for later use");
    }

    @Test
    void testErrorInPostConstructLeavesNothingBehindInCache() {
        OOPDI<ClassFailingPostConstructError> oopdi = new OOPDI<>(ClassFailingPostConstructError.class);

        oopdi.getInstance(ClassFailingPostConstructError.class);
        ClassFailingPostConstructError.constructorCallCount.set(0);

        // The AssertionError surfaces wrapped (reflection wraps it in InvocationTargetException
        // at the invoke boundary); what matters here is that nothing stays cached.
        RuntimeException first = Assertions.assertThrows(RuntimeException.class,
            () -> oopdi.getInstance(ClassFailingPostConstructError.class).ping(),
            "Failing @PostConstruct must propagate");
        Assertions.assertTrue(first.getMessage().contains("Failed to invoke @PostConstruct"));

        Assertions.assertThrows(RuntimeException.class, () -> oopdi.getInstance(ClassFailingPostConstructError.class).ping(),
            "A failed bean must not stay behind half-initialized in the cache, including on Error");

        Assertions.assertEquals(2, ClassFailingPostConstructError.constructorCallCount.get(),
            "Every new request after a failed post-processing must rebuild instead of reusing a broken cached object");
    }

    @Test
    void testRawErrorInPostProcessorRemovesCachedInstance() {
        // Drives InstanceFactory directly with a post-processor that throws a raw Error:
        // Errors bypass every RuntimeException catch, so only the widened compensation
        // (RuntimeException | Error) removes the half-built instance from the cache.
        DependencyResolutionContext stubContext = new DependencyResolutionContext() {
            @Override
            public <A> A getOrCreate(Class<A> clazz) {
                throw new UnsupportedOperationException("not used by this test");
            }

            @Override
            public boolean isDirectConstructionPhase() {
                return false;
            }
        };
        InstanceFactory factory = new InstanceFactory(stubContext, new ClassesResolver(),
                null, new MetadataRepository(MetadataMode.DISABLED),
                () -> ShutdownStatus.ACTIVE, instance -> {
                });
        ScopedInstances scopedInstances = new ScopedInstances(new RequestScopeManager());
        Consumer<Object> failingPostProcessor = instance -> {
            throw new AssertionError("Simulated Error in post-processing");
        };

        ClassFailingPostConstructError.constructorCallCount.set(0);

        AssertionError first = Assertions.assertThrows(AssertionError.class, () -> factory.getOrCreateInjectable(
                ClassFailingPostConstructError.class, scopedInstances, failingPostProcessor, new ThreadLocal<>()),
            "A raw Error must propagate unwrapped");
        Assertions.assertEquals("Simulated Error in post-processing", first.getMessage());

        Assertions.assertThrows(AssertionError.class, () -> factory.getOrCreateInjectable(
                ClassFailingPostConstructError.class, scopedInstances, failingPostProcessor, new ThreadLocal<>()),
            "A failed bean must not stay behind half-initialized in the cache, including on Error");

        Assertions.assertEquals(2, ClassFailingPostConstructError.constructorCallCount.get(),
            "Every new request after a failed post-processing must rebuild instead of reusing a broken cached object");
    }
}
