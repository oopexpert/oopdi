package de.oopexpert.oopdi;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.teststructure.ClassB1;

/**
 * Verifies that concurrent first-time resolutions of the same type share a single classpath
 * scan instead of each scanning (the result cache is filled atomically via
 * {@code computeIfAbsent}).
 */
class TestClassesResolver {

    @Test
    void testConcurrentDetermineRelevantClassScansOnce() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger scanCount = new AtomicInteger();

        ClasspathScanner blockingScanner = new ClasspathScanner() {
            @Override
            public <T> Set<Class<T>> findDerivedClasses(Class<T> parentClass, String packageName) {
                scanCount.incrementAndGet();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for release signal");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                return super.findDerivedClasses(parentClass, packageName);
            }
        };

        ClassesResolver resolver = new ClassesResolver(blockingScanner, new InjectableFilter());

        AtomicReference<Class<?>> resultOne = new AtomicReference<>();
        AtomicReference<Class<?>> resultTwo = new AtomicReference<>();
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();

        Thread workerOne = new Thread(() -> {
            try {
                resultOne.set(resolver.determineRelevantClass(ClassB1.class));
            } catch (Throwable t) {
                workerFailure.compareAndSet(null, t);
            }
        });
        Thread workerTwo = new Thread(() -> {
            try {
                resultTwo.set(resolver.determineRelevantClass(ClassB1.class));
            } catch (Throwable t) {
                workerFailure.compareAndSet(null, t);
            }
        });

        workerOne.start();
        workerTwo.start();

        // Let both workers arrive (inside the scan or parked on the cache lock), then release.
        Thread.sleep(500);
        release.countDown();

        workerOne.join(5000);
        workerTwo.join(5000);

        Assertions.assertNull(workerFailure.get(), "Workers must not fail: " + workerFailure.get());
        Assertions.assertEquals(1, scanCount.get(),
            "Racing first-time resolutions of the same type must share a single classpath scan");
        Assertions.assertSame(ClassB1.class, resultOne.get());
        Assertions.assertSame(ClassB1.class, resultTwo.get());
    }
}
