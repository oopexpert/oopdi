package de.oopexpert.oopdi;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;

final class ConcurrentTestSupport {

    private ConcurrentTestSupport() {
    }

    static void runTwoWorkers(Runnable worker1, Runnable worker2, long timeout, TimeUnit unit) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        Thread t1 = new Thread(() -> runWorker(worker1, start, done));
        Thread t2 = new Thread(() -> runWorker(worker2, start, done));

        t1.start();
        t2.start();
        start.countDown();

        Assertions.assertTrue(done.await(timeout, unit), "Worker threads did not finish in time");
    }

    private static void runWorker(Runnable worker, CountDownLatch start, CountDownLatch done) {
        try {
            start.await();
            worker.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            done.countDown();
        }
    }

}