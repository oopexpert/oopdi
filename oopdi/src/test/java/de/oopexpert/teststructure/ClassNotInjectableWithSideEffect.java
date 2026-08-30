package de.oopexpert.teststructure;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Intentionally NOT annotated with {@code @Injectable}. Used to verify that the framework
 * validates eligibility (annotation presence) *before* invoking this class's constructor, so
 * that requesting an instance of a non-eligible class never triggers this side effect.
 */
public class ClassNotInjectableWithSideEffect {

    public static final AtomicInteger constructorCallCount = new AtomicInteger(0);

    public ClassNotInjectableWithSideEffect() {
        constructorCallCount.incrementAndGet();
    }

}
