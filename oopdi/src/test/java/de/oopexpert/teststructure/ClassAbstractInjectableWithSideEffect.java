package de.oopexpert.teststructure;

import java.util.concurrent.atomic.AtomicInteger;

import de.oopexpert.oopdi.annotation.Injectable;

/**
 * Annotated with {@code @Injectable} but abstract. Used to verify that the framework validates
 * eligibility (non-abstract) *before* invoking this class's constructor via a generated proxy
 * subclass, so that requesting an instance of an abstract class never triggers this side effect.
 */
@Injectable
public abstract class ClassAbstractInjectableWithSideEffect {

    public static final AtomicInteger constructorCallCount = new AtomicInteger(0);

    public ClassAbstractInjectableWithSideEffect() {
        constructorCallCount.incrementAndGet();
    }

}
