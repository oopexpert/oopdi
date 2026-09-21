package de.oopexpert.oopdi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link InstancesState}'s shared instance cache stays consistent when threads
 * create <em>different</em> beans in the same scope concurrently. The per-class locks in
 * {@code InstanceFactory} only serialize creation of the same bean, so the map itself must
 * tolerate parallel writes for distinct keys (a plain {@code LinkedHashMap} does not).
 */
class TestInstancesState {

    /**
     * One distinct key type per worker thread, mimicking distinct bean classes that share one
     * scope's instance cache.
     */
    @SuppressWarnings("rawtypes")
    private static final Class[] KEYS = {
        Integer.class, String.class, Long.class, Double.class,
        Boolean.class, Character.class, Byte.class, Short.class
    };

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    void testConcurrentPutsForDistinctKeysStayConsistent() throws Exception {
        InstancesState state = new InstancesState();

        int threads = KEYS.length;
        int iterations = 200;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final Class key = KEYS[t];
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        if (!start.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting for start signal");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    for (int k = 0; k < iterations; k++) {
                        state.put(key, dummyValueFor(key, k));
                        state.get(key);
                        state.instanceExists(key);
                        state.allInstancesInReverseCreationOrder();
                    }
                    return null;
                }));
            }

            Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS), "All workers should become ready");
            start.countDown();

            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        Assertions.assertEquals(threads, state.allInstances().size(),
            "Every distinct key must have exactly one entry after concurrent writes");
        Assertions.assertEquals(threads, state.allInstancesInReverseCreationOrder().size(),
            "Snapshot views must agree after concurrent modification ended");
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Object dummyValueFor(Class key, int iteration) {
        if (key == Integer.class) {
            return iteration;
        }
        if (key == Long.class) {
            return (long) iteration;
        }
        if (key == Double.class) {
            return (double) iteration;
        }
        if (key == Boolean.class) {
            return iteration % 2 == 0;
        }
        if (key == Character.class) {
            return (char) ('a' + (iteration % 26));
        }
        if (key == Byte.class) {
            return (byte) iteration;
        }
        if (key == Short.class) {
            return (short) iteration;
        }
        return "value-" + iteration;
    }
}
