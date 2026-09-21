package de.oopexpert.teststructure;

import java.util.concurrent.atomic.AtomicInteger;

import de.oopexpert.oopdi.Scope;
import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.annotation.PreDestroy;

/**
 * REQUEST-scoped bean whose cleanup always fails. Used to verify best-effort request-end
 * destruction: a failing cleanup must neither hide the original call outcome nor prevent the
 * destruction of the remaining request-scoped instances.
 */
@Injectable(scope = Scope.REQUEST)
public class ClassRequestFailingPreDestroy {

    public static final AtomicInteger preDestroyCallCount = new AtomicInteger(0);

    public static void reset() {
        preDestroyCallCount.set(0);
    }

    public void ping() {
    }

    @PreDestroy
    public void cleanup() {
        preDestroyCallCount.incrementAndGet();
        throw new IllegalStateException("Simulated request @PreDestroy failure");
    }

}
