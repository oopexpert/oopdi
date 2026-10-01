package de.oopexpert.oopdi;

import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.oopdi.exception.NoClassesLeftAfterFiltering;
import de.oopexpert.teststructure.ClassB;
import de.oopexpert.teststructure.ClassB1;
import de.oopexpert.teststructure.ClassB2;
import de.oopexpert.teststructure.ClassBeanWithJdkOutsideJavaPrefixField;
import de.oopexpert.teststructure.ClassBeanWithPlainField;
import de.oopexpert.teststructure.ClassNestedDepB;
import de.oopexpert.teststructure.ClassNestedOuter;
import de.oopexpert.teststructure.ClassPlainHelper;
import de.oopexpert.teststructure.ClassRoot;
import de.oopexpert.teststructure.ClassSetEmptyRoot;

class TestResolverAndSet {

    @Test
    void testProxyConsistencyInSets() {

        OOPDI<ClassRoot> oopdi = new OOPDI<ClassRoot>(ClassRoot.class);
        oopdi.startup();

        Set<ClassB> classesB = oopdi.getInstance(ClassRoot.class).getClassesB();

        Assertions.assertEquals(1, classesB.size());

        ClassB classBinstance1 = classesB.iterator().next();
        ClassB classBinstance2 = oopdi.getInstance(classBinstance1.getClass());

        Assertions.assertSame(classBinstance1, classBinstance2);

    }

    @Test
    void testInjectSetIncludesProfileSpecificImplementationsWhenProfileIsActive() {

        OOPDI<ClassRoot> oopdi = new OOPDI<>(ClassRoot.class, "profile1");
        oopdi.startup();

        Set<ClassB> classesB = oopdi.getInstance(ClassRoot.class).getClassesB();

        Assertions.assertEquals(2, classesB.size(),
            "When profile1 is active, both ClassB1 (default) and ClassB2(profile1) should be injected");
        Assertions.assertTrue(classesB.stream().anyMatch(ClassB1.class::isInstance));
        Assertions.assertTrue(classesB.stream().anyMatch(ClassB2.class::isInstance));

    }

    @Test
    void testInjectSetReturnsEmptySetWhenNoInjectableImplementationExists() {

        OOPDI<ClassSetEmptyRoot> oopdi = new OOPDI<>(ClassSetEmptyRoot.class);
        oopdi.startup();

        Set<?> values = oopdi.getInstance(ClassSetEmptyRoot.class).getValues();

        Assertions.assertNotNull(values, "Injected set should never be null");
        Assertions.assertTrue(values.isEmpty(),
            "InjectSet should inject an empty set when no injectable implementation exists");

    }

    @Test
    void testProfileFilteredClassCannotBeInstantiatedWhenInactive() {

        OOPDI<ClassRoot> oopdi = new OOPDI<>(ClassRoot.class);
        oopdi.startup();

        Assertions.assertThrows(NoClassesLeftAfterFiltering.class,
            () -> oopdi.getInstance(ClassB2.class).getI(),
            "ClassB2 requires profile1 and should fail when no profile is active");

    }

    @Test
    void testNestedFieldInjectionResolvesRealObjectNotProxy() {

        // The field injection of the nested bean runs while the outer construction chain is
        // still in its direct-construction phase; it must therefore resolve the real object
        // (exact class), not a proxy (ByteBuddy subclass). Before the save/restore fix, the
        // nested chain's finally-block reset the flag and the field ended up holding a proxy.
        OOPDI<ClassNestedOuter> oopdi = new OOPDI<>(ClassNestedOuter.class);
        oopdi.startup();

        ClassNestedDepB depB = oopdi.getInstance(ClassNestedOuter.class).getDepA().getDepB();

        Assertions.assertSame(ClassNestedDepB.class, depB.getClass(),
            "Field injection inside a nested construction chain must yield the real object");

    }

    @Test
    void testUnannotatedFieldOfPlainProjectTypeKeepsConstructorAssignedValue() {

        // Regression test: InstanceDependencyResolver#supports used to apply the
        // constructor-parameter type-name fallback ("anything not java.* is injectable") to
        // fields as well, so this unannotated field was misdetected as an injection point and
        // the framework tried (and failed) to resolve ClassPlainHelper as a managed bean,
        // even though it is not @Injectable and was never meant to be field-injected.
        OOPDI<ClassBeanWithPlainField> oopdi = new OOPDI<>(ClassBeanWithPlainField.class);
        oopdi.startup();

        ClassPlainHelper helper = oopdi.getInstance(ClassBeanWithPlainField.class).getHelper();

        Assertions.assertNotNull(helper);
        Assertions.assertEquals("constructor-assigned", helper.getLabel(),
            "The constructor-assigned helper instance must survive field injection untouched");

    }

    @Test
    void testUnannotatedFieldOfJdkTypeOutsideJavaPrefixKeepsConstructorAssignedValue() {

        // Regression test: the type-name fallback only excluded "java."-prefixed types, so
        // javax.* types (outside that prefix) on unannotated fields were misdetected as
        // injection points too, failing with CannotInject ("it is not annotated as
        // 'Injectable'") instead of leaving the constructor-assigned value alone.
        OOPDI<ClassBeanWithJdkOutsideJavaPrefixField> oopdi = new OOPDI<>(ClassBeanWithJdkOutsideJavaPrefixField.class);
        oopdi.startup();

        Assertions.assertNotNull(
            oopdi.getInstance(ClassBeanWithJdkOutsideJavaPrefixField.class).getTimer(),
            "The constructor-assigned javax.swing.Timer instance must survive field injection untouched");

    }

}
