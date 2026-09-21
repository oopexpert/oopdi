package de.oopexpert.teststructure;

import java.util.concurrent.atomic.AtomicInteger;

import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.annotation.PostConstruct;

/**
 * Simulates a bean whose {@code @PostConstruct} initialization always fails. Used to verify
 * that a failed post-processing step does not leave a half-initialized instance behind in the
 * cache: every new request must rebuild (constructor runs again) instead of reusing a broken
 * cached object.
 */
@Injectable
public class ClassFailingPostConstruct {

    public static final AtomicInteger constructorCallCount = new AtomicInteger(0);

    public ClassFailingPostConstruct() {
        constructorCallCount.incrementAndGet();
    }

    @PostConstruct
    public void init() {
        throw new IllegalStateException("Simulated @PostConstruct failure");
    }

    public void ping() {
    }

}
