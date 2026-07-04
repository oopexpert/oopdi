package de.oopexpert.teststructure;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassGlobalRace {

    public static final AtomicInteger instanceCount = new AtomicInteger(0);
    public static volatile CountDownLatch constructorStartedLatch;

    public ClassGlobalRace() {
        instanceCount.incrementAndGet();
        // Signal that we have entered the constructor (Thread 1 is now holding
        // the scopedMap lock inside createInstance).
        CountDownLatch latch = constructorStartedLatch;
        if (latch != null) {
            latch.countDown();
        }
        // Sleep while holding the scopedMap lock so that the other thread has time
        // to pass the instanceExists() check (which is outside the lock) before
        // Thread 1 calls scopedMap.put().
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public int getCount() {
        return instanceCount.get();
    }

}
