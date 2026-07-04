package de.oopexpert.oopdi;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.teststructure.ClassGlobalRace;
import de.oopexpert.teststructure.ClassParallelA;
import de.oopexpert.teststructure.ClassParallelB;
import de.oopexpert.teststructure.ClassParallelInitTracker;
import de.oopexpert.teststructure.ClassRequestScenario;
import de.oopexpert.teststructure.ClassRequestState;

class TestRequestAndConcurrency {

    /**
     * Demonstrates the GLOBAL scope check-then-act race condition in getOrCreateInjectable.
     */
    @Test
    void testGlobalScopeRaceConditionProducesDuplicateInstances() throws InterruptedException {

        CountDownLatch constructorStartedLatch = new CountDownLatch(1);

        OOPDI<ClassGlobalRace> oopdi = new OOPDI<>(ClassGlobalRace.class);
        ClassGlobalRace proxy = oopdi.getInstance(ClassGlobalRace.class);

        ClassGlobalRace.instanceCount.set(0);
        ClassGlobalRace.constructorStartedLatch = constructorStartedLatch;

        Thread thread1 = new Thread(() -> proxy.getCount());
        thread1.start();

        constructorStartedLatch.await(5, TimeUnit.SECONDS);

        Thread thread2 = new Thread(() -> proxy.getCount());
        thread2.start();

        thread1.join(5000);
        thread2.join(5000);

        Assertions.assertEquals(1, ClassGlobalRace.instanceCount.get(),
                "Race condition: both threads passed instanceExists=false before any put() " +
                "and each created a separate real object");
    }

    @Test
    void testRequestScopeNestedCallsShareSameRequestScopedState() {

        OOPDI<ClassRequestScenario> oopdi = new OOPDI<>(ClassRequestScenario.class);
        ClassRequestScenario scenario = oopdi.getInstance(ClassRequestScenario.class);
        ClassRequestState.resetCounter();

        ClassRequestScenario.Result result = scenario.execute(77, false);

        Assertions.assertEquals(77, result.getReadFromReader(),
            "Nested REQUEST-scoped call should read the value written in the same call chain");
        Assertions.assertEquals(result.getIdInScenario(), result.getIdInReader(),
            "Both collaborators should resolve to the same REQUEST-scoped state instance in one call chain");

    }

    @Test
    void testRequestScopeExceptionCleansContextForNextCall() {

        OOPDI<ClassRequestScenario> oopdi = new OOPDI<>(ClassRequestScenario.class);
        ClassRequestScenario scenario = oopdi.getInstance(ClassRequestScenario.class);
        ClassRequestState.resetCounter();

        java.lang.reflect.InvocationTargetException ex = Assertions.assertThrows(
            java.lang.reflect.InvocationTargetException.class,
            () -> scenario.execute(1, true),
            "Failure in a proxied call must still clear request scope in finally"
        );
        Assertions.assertTrue(ex.getCause() instanceof IllegalStateException,
            "The proxied method exception should be preserved as InvocationTargetException cause");

        ClassRequestScenario.Result second = scenario.execute(2, false);
        ClassRequestScenario.Result third = scenario.execute(3, false);

        Assertions.assertEquals(2, second.getReadFromReader());
        Assertions.assertEquals(3, third.getReadFromReader());
        Assertions.assertNotEquals(second.getIdInScenario(), third.getIdInScenario(),
            "Each top-level call should get a fresh REQUEST-scoped state, including after an exception");

    }

    @Test
    void testRequestScopeIsolatedAcrossThreads() throws InterruptedException {

        OOPDI<ClassRequestScenario> oopdi = new OOPDI<>(ClassRequestScenario.class);
        ClassRequestScenario scenario = oopdi.getInstance(ClassRequestScenario.class);
        ClassRequestState.resetCounter();

        AtomicInteger idThreadOne = new AtomicInteger(-1);
        AtomicInteger idThreadTwo = new AtomicInteger(-1);

        ConcurrentTestSupport.runTwoWorkers(() -> {
            ClassRequestScenario.Result result = scenario.execute(111, false);
            idThreadOne.set(result.getIdInScenario());
            Assertions.assertEquals(111, result.getReadFromReader());
        }, () -> {
            ClassRequestScenario.Result result = scenario.execute(222, false);
            idThreadTwo.set(result.getIdInScenario());
            Assertions.assertEquals(222, result.getReadFromReader());
        }, 5, TimeUnit.SECONDS);

        Assertions.assertNotEquals(idThreadOne.get(), idThreadTwo.get(),
            "REQUEST scope instances must be isolated across threads");

    }

    @Test
    void testParallelInitializationForDifferentGlobalBeans() throws InterruptedException {

        OOPDI<ClassParallelA> oopdi = new OOPDI<>(ClassParallelA.class);
        ClassParallelA beanA = oopdi.getInstance(ClassParallelA.class);
        ClassParallelB beanB = oopdi.getInstance(ClassParallelB.class);

        ClassParallelInitTracker.resetAndEnable();

        Thread workers = new Thread(() -> {
            try {
                ConcurrentTestSupport.runTwoWorkers(beanA::touch, beanB::touch, 5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        workers.start();

        Assertions.assertTrue(
            ClassParallelInitTracker.awaitBothStarted(5, TimeUnit.SECONDS),
            "Different GLOBAL beans should be allowed to initialize in parallel"
        );

        ClassParallelInitTracker.releaseConstructors();
        workers.join(5000);
        ClassParallelInitTracker.disable();

        Assertions.assertTrue(
            ClassParallelInitTracker.getMaxConcurrentInits() >= 2,
            "Per-class lock granularity should allow parallel initialization of unrelated beans"
        );

    }

}
