package de.oopexpert.oopdi;

import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.oopdi.exception.NoClassesLeftAfterFiltering;
import de.oopexpert.teststructure.ClassB;
import de.oopexpert.teststructure.ClassB1;
import de.oopexpert.teststructure.ClassB2;
import de.oopexpert.teststructure.ClassRoot;
import de.oopexpert.teststructure.ClassSetEmptyRoot;

class TestResolverAndSet {

    @Test
    void testProxyConsistencyInSets() {

        OOPDI<ClassRoot> oopdi = new OOPDI<ClassRoot>(ClassRoot.class);

        Set<ClassB> classesB = oopdi.getInstance(ClassRoot.class).getClassesB();

        Assertions.assertEquals(1, classesB.size());

        ClassB classBinstance1 = classesB.iterator().next();
        ClassB classBinstance2 = oopdi.getInstance(classBinstance1.getClass());

        Assertions.assertSame(classBinstance1, classBinstance2);

    }

    @Test
    void testInjectSetIncludesProfileSpecificImplementationsWhenProfileIsActive() {

        OOPDI<ClassRoot> oopdi = new OOPDI<>(ClassRoot.class, "profile1");

        Set<ClassB> classesB = oopdi.getInstance(ClassRoot.class).getClassesB();

        Assertions.assertEquals(2, classesB.size(),
            "When profile1 is active, both ClassB1 (default) and ClassB2(profile1) should be injected");
        Assertions.assertTrue(classesB.stream().anyMatch(ClassB1.class::isInstance));
        Assertions.assertTrue(classesB.stream().anyMatch(ClassB2.class::isInstance));

    }

    @Test
    void testInjectSetReturnsEmptySetWhenNoInjectableImplementationExists() {

        OOPDI<ClassSetEmptyRoot> oopdi = new OOPDI<>(ClassSetEmptyRoot.class);

        Set<?> values = oopdi.getInstance(ClassSetEmptyRoot.class).getValues();

        Assertions.assertNotNull(values, "Injected set should never be null");
        Assertions.assertTrue(values.isEmpty(),
            "InjectSet should inject an empty set when no injectable implementation exists");

    }

    @Test
    void testProfileFilteredClassCannotBeInstantiatedWhenInactive() {

        OOPDI<ClassRoot> oopdi = new OOPDI<>(ClassRoot.class);

        Assertions.assertThrows(NoClassesLeftAfterFiltering.class,
            () -> oopdi.getInstance(ClassB2.class).getI(),
            "ClassB2 requires profile1 and should fail when no profile is active");

    }

}
