package de.oopexpert.teststructure;

import java.util.concurrent.atomic.AtomicInteger;

import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.annotation.PostConstruct;

@Injectable
public class ClassFailingPostConstructError {

    public static final AtomicInteger constructorCallCount = new AtomicInteger(0);

    public ClassFailingPostConstructError() {
        constructorCallCount.incrementAndGet();
    }

    @PostConstruct
    public void init() {
        throw new AssertionError("Simulated @PostConstruct Error");
    }

    public void ping() {
    }

}
