package de.oopexpert.teststructure;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class ClassParallelInitTracker {

    private static final AtomicBoolean enabled = new AtomicBoolean(false);
    private static final AtomicInteger currentInits = new AtomicInteger(0);
    private static final AtomicInteger maxConcurrentInits = new AtomicInteger(0);

    private static volatile CountDownLatch bothStarted = new CountDownLatch(2);
    private static volatile CountDownLatch release = new CountDownLatch(1);

    private ClassParallelInitTracker() {
    }

    public static void resetAndEnable() {
        currentInits.set(0);
        maxConcurrentInits.set(0);
        bothStarted = new CountDownLatch(2);
        release = new CountDownLatch(1);
        enabled.set(true);
    }

    public static void disable() {
        enabled.set(false);
    }

    public static boolean awaitBothStarted(long timeout, TimeUnit unit) throws InterruptedException {
        return bothStarted.await(timeout, unit);
    }

    public static void releaseConstructors() {
        release.countDown();
    }

    public static int getMaxConcurrentInits() {
        return maxConcurrentInits.get();
    }

    public static void onConstructorEnterAndAwaitRelease() {
        if (!enabled.get()) {
            return;
        }

        int now = currentInits.incrementAndGet();
        maxConcurrentInits.updateAndGet(previous -> Math.max(previous, now));
        bothStarted.countDown();

        try {
            // Keep constructors paused so concurrent initialization can be observed deterministically.
            release.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            currentInits.decrementAndGet();
        }
    }

}
