package de.oopexpert.oopdi;

import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.teststructure.ClassA;
import de.oopexpert.teststructure.ClassB;
import de.oopexpert.teststructure.ClassB1;
import de.oopexpert.teststructure.ClassC;
import de.oopexpert.teststructure.ClassD;
import de.oopexpert.teststructure.ClassRoot;

class TestScopeBehavior {

    @Test
    void testScopeLocal() {

        OOPDI<ClassRoot> oopdi = new OOPDI<ClassRoot>(ClassRoot.class);

        ClassB classBinstance = oopdi.getInstance(ClassB1.class);

        classBinstance.setI(3);
        Integer result = classBinstance.getI();

        Assertions.assertEquals(0, result);

    }

    @Test
    void testThreadScope() throws InterruptedException {

        OOPDI<ClassRoot> oopdi = new OOPDI<ClassRoot>(ClassRoot.class);

        ClassC instance = oopdi.getInstance(ClassC.class);

        int expected = 3;
        instance.setI(expected);
        Assertions.assertEquals(expected, instance.getI());

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                ClassC instance = oopdi.getInstance(ClassC.class);
                Assertions.assertNotEquals(expected, instance.getI());
            }
        });

        thread.start();
        thread.join();

    }

    @Test
    void testGlobalScope() throws InterruptedException {

        OOPDI<ClassRoot> oopdi = new OOPDI<ClassRoot>(ClassRoot.class);

        ClassA instance = oopdi.getInstance(ClassA.class);

        int expected = 3;
        instance.setI(expected);
        Assertions.assertEquals(expected, instance.getI());

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                ClassA instance = oopdi.getInstance(ClassA.class);
                Assertions.assertEquals(expected, instance.getI());
            }
        });

        thread.start();
        thread.join();

    }

    @Test
    void testRequestScope() throws InterruptedException {

        OOPDI<ClassRoot> oopdi = new OOPDI<ClassRoot>(ClassRoot.class);

        ClassD instance = oopdi.getInstance(ClassD.class);

        int expected = 3;
        instance.setI(expected);
        Assertions.assertNotEquals(expected, instance.getI());

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                ClassA instance = oopdi.getInstance(ClassA.class);
                Assertions.assertNotEquals(expected, instance.getI());
            }
        });

        thread.start();
        thread.join();

        Consumer<ClassD> consumerClassD = new Consumer<ClassD>() {

            @Override
            public void accept(ClassD classD) {
                Assertions.assertEquals(ClassRoot.TEST_I_CLASS_D, classD.getI());
            }

        };

        ClassRoot instanceClassRoot = oopdi.getInstance(ClassRoot.class);

        instanceClassRoot.execute(consumerClassD);

    }

}
