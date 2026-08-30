package de.oopexpert.oopdi;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.teststructure.ClassAbstractInjectableWithSideEffect;
import de.oopexpert.teststructure.ClassNotInjectableWithSideEffect;

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

}
