package de.oopexpert.teststructure;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import de.oopexpert.oopdi.Scope;
import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.annotation.PreDestroy;

/**
 * REQUEST-scoped bean with lifecycle tracking. Used to verify that request-scoped beans are
 * destroyed exactly once when their call chain ends (and never mid-chain), including for
 * nested call chains which share one request state.
 */
@Injectable(scope = Scope.REQUEST)
public class ClassRequestPreDestroy {

    public static final AtomicInteger preDestroyCallCount = new AtomicInteger(0);
    public static final List<Object> destroyedInstances = new CopyOnWriteArrayList<>();

    private int value;

    public static void reset() {
        preDestroyCallCount.set(0);
        destroyedInstances.clear();
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public void execute(Consumer<ClassRequestPreDestroy> consumer) {
        consumer.accept(this);
    }

    @PreDestroy
    public void cleanup() {
        preDestroyCallCount.incrementAndGet();
        destroyedInstances.add(this);
    }

}
