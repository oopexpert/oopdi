package de.oopexpert.teststructure;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.annotation.PreDestroy;

@Injectable
public class ClassSlowConstruction {

    public static volatile CountDownLatch enteredConstructor;
    public static volatile CountDownLatch releaseConstructor;
    public static final AtomicInteger preDestroyCallCount = new AtomicInteger(0);

    public ClassSlowConstruction() {
        CountDownLatch entered = enteredConstructor;
        if (entered != null) {
            entered.countDown();
        }
        try {
            CountDownLatch release = releaseConstructor;
            if (release != null) {
                release.await(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void ping() {
    }

    @PreDestroy
    public void cleanup() {
        preDestroyCallCount.incrementAndGet();
    }

}
