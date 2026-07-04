package de.oopexpert.oopdi;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.teststructure.ClassA;
import de.oopexpert.teststructure.ClassPostConstructChild;
import de.oopexpert.teststructure.ClassPostConstructWithParameters;
import de.oopexpert.teststructure.ClassPreDestroyChild;
import de.oopexpert.teststructure.ClassWithPreDestroy;

class TestLifecycleHooks {

    @Test
    void testPostConstructInSuperclassIsInvoked() {

        OOPDI<ClassPostConstructChild> oopdi = new OOPDI<>(ClassPostConstructChild.class);

        ClassPostConstructChild instance = oopdi.getInstance(ClassPostConstructChild.class);

        Assertions.assertTrue(instance.isBaseInitialized(),
            "@PostConstruct method declared in abstract superclass should be invoked");

    }

    @Test
    void testPostConstructReceivesBeanAndOopdiParameters() {

        OOPDI<ClassPostConstructWithParameters> oopdi = new OOPDI<>(ClassPostConstructWithParameters.class);
        ClassPostConstructWithParameters instance = oopdi.getInstance(ClassPostConstructWithParameters.class);

        Assertions.assertTrue(instance.isInitialized(), "@PostConstruct should be invoked");
        Assertions.assertTrue(instance.isClassAInjectedIntoPostConstruct(),
            "Bean parameter should be resolved and injected into @PostConstruct");
        Assertions.assertTrue(instance.isOopdiInjectedIntoPostConstruct(),
            "OOPDI parameter should be resolved and injected into @PostConstruct");

    }

    @Test
    void testPreDestroyIsInvokedOnShutdown() {

        OOPDI<ClassWithPreDestroy> oopdi = new OOPDI<>(ClassWithPreDestroy.class);

        ClassWithPreDestroy instance = oopdi.getInstance(ClassWithPreDestroy.class);
        Assertions.assertFalse(instance.isDestroyed(), "should not be destroyed before shutdown");

        oopdi.shutdown();

        Assertions.assertTrue(instance.isDestroyed(), "@PreDestroy method should be called on shutdown");

    }

    @Test
    void testPreDestroyInSuperclassIsInvokedOnShutdown() {

        OOPDI<ClassPreDestroyChild> oopdi = new OOPDI<>(ClassPreDestroyChild.class);

        ClassPreDestroyChild instance = oopdi.getInstance(ClassPreDestroyChild.class);
        Assertions.assertFalse(instance.isBaseDestroyed(), "should not be destroyed before shutdown");

        oopdi.shutdown();

        Assertions.assertTrue(instance.isBaseDestroyed(),
            "@PreDestroy method declared in abstract superclass should be called on shutdown");

    }

}
