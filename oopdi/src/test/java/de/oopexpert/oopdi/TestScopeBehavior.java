package de.oopexpert.oopdi;

import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.teststructure.ClassA;
import de.oopexpert.teststructure.ClassB;
import de.oopexpert.teststructure.ClassB1;
import de.oopexpert.teststructure.ClassC;
import de.oopexpert.teststructure.ClassD;
import de.oopexpert.teststructure.ClassGlobalRace;
import de.oopexpert.teststructure.ClassImmediateLocalMisconfig;
import de.oopexpert.teststructure.ClassImmediateRequestMisconfig;
import de.oopexpert.teststructure.ClassImmediateThreadMisconfig;
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
    void testGlobalScopeIsolatedAcrossDifferentContainers() {

        OOPDI<ClassRoot> oopdiOne = new OOPDI<>(ClassRoot.class);
        OOPDI<ClassRoot> oopdiTwo = new OOPDI<>(ClassRoot.class);

        ClassA one = oopdiOne.getInstance(ClassA.class);
        ClassA two = oopdiTwo.getInstance(ClassA.class);

        one.setI(123);

        Assertions.assertEquals(123, one.getI(),
            "First container should reflect its own GLOBAL scoped state");
        Assertions.assertNotEquals(123, two.getI(),
            "Different OOPDI containers must not share GLOBAL scoped instances");

    }

    @Test
    void testRequestScopeIsolatedAcrossDifferentContainers() {

        OOPDI<ClassD> oopdiOne = new OOPDI<>(ClassD.class);
        OOPDI<ClassD> oopdiTwo = new OOPDI<>(ClassD.class);

        ClassD one = oopdiOne.getInstance(ClassD.class);
        ClassD two = oopdiTwo.getInstance(ClassD.class);

        java.util.concurrent.atomic.AtomicInteger readInTwo = new java.util.concurrent.atomic.AtomicInteger(-1);
        java.util.concurrent.atomic.AtomicInteger readInOneAfterNested = new java.util.concurrent.atomic.AtomicInteger(-1);

        one.execute(dOne -> {
            dOne.setI(1);
            two.execute(dTwo -> {
                dTwo.setI(2);
                readInTwo.set(dTwo.getI());
            });
            readInOneAfterNested.set(dOne.getI());
        });

        Assertions.assertEquals(2, readInTwo.get());
        Assertions.assertEquals(1, readInOneAfterNested.get(),
            "A nested REQUEST chain of a second container must not see or overwrite the first container's REQUEST state");

    }

    @Test
    void testGlobalScopeRealObjectResolvedOnceAcrossManyProxyCalls() {

        ClassGlobalRace.instanceCount.set(0);

        OOPDI<ClassGlobalRace> oopdi = new OOPDI<>(ClassGlobalRace.class);
        ClassGlobalRace instance = oopdi.getInstance(ClassGlobalRace.class);

        // First method call resolves (and caches) the real object.
        instance.getCount();
        int countAfterFirstCall = ClassGlobalRace.instanceCount.get();

        for (int i = 0; i < 50; i++) {
            instance.getCount();
        }

        Assertions.assertEquals(countAfterFirstCall, ClassGlobalRace.instanceCount.get(),
            "GLOBAL scoped real object must be resolved once and cached, not re-resolved on every proxy method call");

    }

    @Test
    void testImmediateThreadScopeMisconfigurationThrows() {

        OOPDI<ClassImmediateThreadMisconfig> oopdi = new OOPDI<>(ClassImmediateThreadMisconfig.class);
        ClassImmediateThreadMisconfig instance = oopdi.getInstance(ClassImmediateThreadMisconfig.class);

        RuntimeException ex = Assertions.assertThrows(RuntimeException.class, instance::ping,
            "THREAD scope with immediate=true should be rejected");
        Assertions.assertTrue(ex.getMessage().contains("Misconfiguration"));

    }

    @Test
    void testImmediateLocalScopeMisconfigurationThrows() {

        OOPDI<ClassImmediateLocalMisconfig> oopdi = new OOPDI<>(ClassImmediateLocalMisconfig.class);
        ClassImmediateLocalMisconfig instance = oopdi.getInstance(ClassImmediateLocalMisconfig.class);

        RuntimeException ex = Assertions.assertThrows(RuntimeException.class, instance::ping,
            "LOCAL scope with immediate=true should be rejected");
        Assertions.assertTrue(ex.getMessage().contains("Misconfiguration"));

    }

    @Test
    void testImmediateRequestScopeMisconfigurationThrows() {

        OOPDI<ClassImmediateRequestMisconfig> oopdi = new OOPDI<>(ClassImmediateRequestMisconfig.class);
        ClassImmediateRequestMisconfig instance = oopdi.getInstance(ClassImmediateRequestMisconfig.class);

        RuntimeException ex = Assertions.assertThrows(RuntimeException.class, instance::ping,
            "REQUEST scope with immediate=true should be rejected");
        Assertions.assertTrue(ex.getMessage().contains("Misconfiguration"));

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
