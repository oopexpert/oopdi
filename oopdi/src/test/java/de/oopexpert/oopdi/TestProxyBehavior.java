package de.oopexpert.oopdi;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.teststructure.ClassProxyExceptionTarget;
import de.oopexpert.teststructure.ClassProxyReturnTarget;

class TestProxyBehavior {

    @Test
    void testProxyMethodReturnValuePassesThrough() {

        OOPDI<ClassProxyReturnTarget> oopdi = new OOPDI<>(ClassProxyReturnTarget.class);

        ClassProxyReturnTarget target = oopdi.getInstance(ClassProxyReturnTarget.class);

        Assertions.assertEquals(9, target.add(4, 5),
            "Proxy should pass return values from real object unchanged");

    }

    @Test
    void testProxyMethodExceptionPreservesCause() {

        OOPDI<ClassProxyExceptionTarget> oopdi = new OOPDI<>(ClassProxyExceptionTarget.class);

        ClassProxyExceptionTarget target = oopdi.getInstance(ClassProxyExceptionTarget.class);

        java.lang.reflect.InvocationTargetException ex = Assertions.assertThrows(
            java.lang.reflect.InvocationTargetException.class,
            target::fail,
            "Proxy invocation should surface the reflected method-invoke exception"
        );
        Assertions.assertTrue(ex.getCause() instanceof IllegalArgumentException);
        Assertions.assertEquals("boom", ex.getCause().getMessage());

    }

}
