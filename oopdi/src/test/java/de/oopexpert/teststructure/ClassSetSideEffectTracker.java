package de.oopexpert.teststructure;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Separate tracker class so tests can observe whether {@link ClassSetSideEffectCandidate} was
 * initialized without ever referencing that class themselves (referencing a class's static
 * field triggers its own initialization, which would make the test self-defeating).
 */
public final class ClassSetSideEffectTracker {

    public static final AtomicInteger staticInitCount = new AtomicInteger(0);

    private ClassSetSideEffectTracker() {
    }

}
