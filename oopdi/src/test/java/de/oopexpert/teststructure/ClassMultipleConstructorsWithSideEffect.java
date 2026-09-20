package de.oopexpert.teststructure;

import java.util.concurrent.atomic.AtomicInteger;

import de.oopexpert.oopdi.annotation.Injectable;

/**
 * Declares two constructors on purpose. Used to verify that the "exactly one constructor"
 * invariant is validated by {@code MetadataRepository} (throwing {@code MultipleConstructors})
 * before any constructor of this class is actually invoked, whether the request goes through
 * proxy creation or real object instantiation.
 */
@Injectable
public class ClassMultipleConstructorsWithSideEffect {

    public static final AtomicInteger constructorCallCount = new AtomicInteger(0);

    public ClassMultipleConstructorsWithSideEffect() {
        constructorCallCount.incrementAndGet();
    }

    public ClassMultipleConstructorsWithSideEffect(String ignored) {
        constructorCallCount.incrementAndGet();
    }

}
