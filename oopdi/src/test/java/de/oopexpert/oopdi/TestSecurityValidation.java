package de.oopexpert.oopdi;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.teststructure.ClassAbstractInjectableWithSideEffect;
import de.oopexpert.teststructure.ClassNotInjectableWithSideEffect;
import de.oopexpert.teststructure.ClassSetSideEffectRoot;
import de.oopexpert.teststructure.ClassSetSideEffectTracker;

/**
 * Verifies that eligibility validation (annotation presence, non-abstract) runs *before* any
 * proxy or real constructor is invoked, so that requesting an ineligible class never triggers
 * its constructor as a side effect of proxy creation.
 */
class TestSecurityValidation {

    @Test
    void testNonInjectableClassConstructorNotInvokedBeforeValidation() {

        ClassNotInjectableWithSideEffect.constructorCallCount.set(0);

        OOPDI<ClassNotInjectableWithSideEffect> oopdi = new OOPDI<>(ClassNotInjectableWithSideEffect.class);

        RuntimeException ex = Assertions.assertThrows(RuntimeException.class,
            () -> oopdi.getInstance(ClassNotInjectableWithSideEffect.class),
            "Requesting a non-@Injectable class must throw before construction");

        Assertions.assertTrue(ex.getMessage().contains("not annotated as 'Injectable'"));
        Assertions.assertEquals(0, ClassNotInjectableWithSideEffect.constructorCallCount.get(),
            "Constructor must not run as a side effect of proxy creation for an ineligible class");

    }

    @Test
    void testAbstractClassConstructorNotInvokedBeforeValidation() {

        ClassAbstractInjectableWithSideEffect.constructorCallCount.set(0);

        OOPDI<ClassAbstractInjectableWithSideEffect> oopdi = new OOPDI<>(ClassAbstractInjectableWithSideEffect.class);

        RuntimeException ex = Assertions.assertThrows(RuntimeException.class,
            () -> oopdi.getInstance(ClassAbstractInjectableWithSideEffect.class),
            "Requesting an abstract class must throw before construction");

        Assertions.assertTrue(ex.getMessage().contains("abstract"));
        Assertions.assertEquals(0, ClassAbstractInjectableWithSideEffect.constructorCallCount.get(),
            "Constructor must not run as a side effect of proxy creation for an abstract class");

    }

    @Test
    void testInjectSetClasspathScanDoesNotInitializeProfileFilteredCandidate() {

        OOPDI<ClassSetSideEffectRoot> oopdi = new OOPDI<>(ClassSetSideEffectRoot.class);

        // No profile active, so the candidate implementation (requires
        // "profile-never-active-for-side-effect-test") must be filtered out; the classpath scan
        // itself must not have triggered its static initializer while loading/checking it for
        // assignability and profile membership.
        Assertions.assertTrue(oopdi.getInstance(ClassSetSideEffectRoot.class).getValues().isEmpty(),
            "Profile-mismatched candidate must not be included in the injected set");

        Assertions.assertEquals(0, ClassSetSideEffectTracker.staticInitCount.get(),
            "Classpath scan must not initialize a class that ends up filtered out by profile mismatch");

    }

}
