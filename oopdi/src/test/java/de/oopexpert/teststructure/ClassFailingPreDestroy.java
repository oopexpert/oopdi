package de.oopexpert.teststructure;

import java.util.concurrent.atomic.AtomicInteger;

import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.annotation.PreDestroy;

/**
 * Simulates a bean whose {@code @PreDestroy} cleanup always fails. Used to verify best-effort
 * shutdown: a failing cleanup must not prevent the destruction of the remaining instances, and
 * the individual failures must be aggregated instead of aborting the shutdown.
 */
@Injectable
public class ClassFailingPreDestroy {

    public static final AtomicInteger preDestroyCallCount = new AtomicInteger(0);

    @PreDestroy
    public void cleanup() {
        preDestroyCallCount.incrementAndGet();
        throw new IllegalStateException("Simulated @PreDestroy failure");
    }

    public void ping() {
    }

}
