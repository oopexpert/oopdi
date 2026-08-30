package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.Injectable;

/**
 * Requires a profile that is never activated in tests. Used to verify that classpath scanning
 * for {@code @InjectSet} does not trigger this class's static initializer merely by loading it
 * for assignability/annotation checks, i.e. that it is filtered out by profile mismatch before
 * ever being initialized. The side effect is recorded on {@link ClassSetSideEffectTracker}
 * rather than a field on this class, so a test can observe it without itself referencing (and
 * thereby initializing) this class.
 */
@Injectable(profiles = {"profile-never-active-for-side-effect-test"})
public class ClassSetSideEffectCandidate implements ClassSetSideEffectHint {

    static {
        ClassSetSideEffectTracker.staticInitCount.incrementAndGet();
    }

}
