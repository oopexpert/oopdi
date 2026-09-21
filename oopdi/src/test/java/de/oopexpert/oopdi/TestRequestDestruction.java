package de.oopexpert.oopdi;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.teststructure.ClassRequestFailingPreDestroy;
import de.oopexpert.teststructure.ClassRequestPreDestroy;

/**
 * Verifies that REQUEST-scoped beans are destroyed when their call chain ends (previously their
 * {@code @PreDestroy} methods silently never ran): exactly once, in reverse creation order,
 * best-effort with aggregated failures, and exactly once for nested chains sharing one request
 * state.
 */
class TestRequestDestruction {

    @Test
    void testRequestBeanDestroyedExactlyOnceAtChainEnd() {
        OOPDI<ClassRequestPreDestroy> oopdi = new OOPDI<>(ClassRequestPreDestroy.class);
        ClassRequestPreDestroy proxy = oopdi.getInstance(ClassRequestPreDestroy.class);
        ClassRequestPreDestroy.reset();

        AtomicInteger countMidChain = new AtomicInteger(-1);
        AtomicReference<Object> chainInstance = new AtomicReference<>();

        proxy.execute(bean -> {
            bean.setValue(7);
            countMidChain.set(ClassRequestPreDestroy.preDestroyCallCount.get());
            chainInstance.set(bean);
        });

        Assertions.assertEquals(0, countMidChain.get(),
            "Nothing may be destroyed while the request chain is still running");
        Assertions.assertEquals(1, ClassRequestPreDestroy.preDestroyCallCount.get(),
            "The request bean must be destroyed exactly once when its chain ends");
        Assertions.assertSame(chainInstance.get(), ClassRequestPreDestroy.destroyedInstances.get(0),
            "The destroyed instance must be the one the chain actually used");
    }

    @Test
    void testNestedChainsShareStateAndDestroyOnceAtOutermostEnd() {
        OOPDI<ClassRequestPreDestroy> oopdi = new OOPDI<>(ClassRequestPreDestroy.class);
        ClassRequestPreDestroy outer = oopdi.getInstance(ClassRequestPreDestroy.class);
        ClassRequestPreDestroy inner = oopdi.getInstance(ClassRequestPreDestroy.class);
        ClassRequestPreDestroy.reset();

        AtomicInteger valueSeenByOuterAfterNested = new AtomicInteger(-1);

        outer.execute(outerBean -> {
            outerBean.setValue(1);
            inner.execute(innerBean -> innerBean.setValue(2));
            valueSeenByOuterAfterNested.set(outerBean.getValue());
        });

        Assertions.assertEquals(2, valueSeenByOuterAfterNested.get(),
            "Nested calls must share the outermost chain's request state");
        Assertions.assertEquals(1, ClassRequestPreDestroy.preDestroyCallCount.get(),
            "One shared request bean must be destroyed exactly once, at the outermost chain end");
    }

    @Test
    void testFailingRequestPreDestroyIsAggregatedButDestroysTheRest() {
        OOPDI<ClassRequestPreDestroy> oopdi = new OOPDI<>(ClassRequestPreDestroy.class);
        ClassRequestPreDestroy good = oopdi.getInstance(ClassRequestPreDestroy.class);
        ClassRequestFailingPreDestroy bad = oopdi.getInstance(ClassRequestFailingPreDestroy.class);
        ClassRequestPreDestroy.reset();
        ClassRequestFailingPreDestroy.reset();

        // Both beans land in the same request state; the bad one is created second, so it is
        // destroyed first (reverse creation order) and must not block the good one.
        RuntimeException ex = Assertions.assertThrows(RuntimeException.class, () -> good.execute(g -> bad.ping()),
            "Failing request-end cleanup must be reported when the main call succeeded");

        Assertions.assertEquals(1, ClassRequestFailingPreDestroy.preDestroyCallCount.get());
        Assertions.assertEquals(1, ClassRequestPreDestroy.preDestroyCallCount.get(),
            "Remaining request beans must still be destroyed best-effort after a cleanup failure");
        Assertions.assertFalse(ex.getSuppressed().length == 0,
            "Cleanup failures must be aggregated as suppressed exceptions");
    }

    @Test
    void testFailingRequestPreDestroyDoesNotHideMainCallFailure() throws Exception {
        OOPDI<ClassRequestPreDestroy> oopdi = new OOPDI<>(ClassRequestPreDestroy.class);
        ClassRequestPreDestroy good = oopdi.getInstance(ClassRequestPreDestroy.class);
        ClassRequestFailingPreDestroy bad = oopdi.getInstance(ClassRequestFailingPreDestroy.class);
        ClassRequestPreDestroy.reset();
        ClassRequestFailingPreDestroy.reset();

        java.lang.reflect.InvocationTargetException ex = Assertions.assertThrows(
            java.lang.reflect.InvocationTargetException.class,
            () -> good.execute(g -> {
                bad.ping();
                throw new IllegalStateException("boom");
            }),
            "The main call failure must propagate");

        Assertions.assertEquals("boom", ex.getCause().getMessage());
        Assertions.assertFalse(ex.getSuppressed().length == 0,
            "Cleanup failures must be attached as suppressed exceptions without hiding the main failure");
        Assertions.assertEquals(1, ClassRequestPreDestroy.preDestroyCallCount.get(),
            "Remaining request beans must still be destroyed best-effort");
    }

    @Test
    void testNextChainStartsFreshAfterDestruction() {
        OOPDI<ClassRequestPreDestroy> oopdi = new OOPDI<>(ClassRequestPreDestroy.class);
        ClassRequestPreDestroy proxy = oopdi.getInstance(ClassRequestPreDestroy.class);
        ClassRequestPreDestroy.reset();

        proxy.execute(bean -> bean.setValue(11));

        AtomicInteger secondChainValue = new AtomicInteger(-1);
        proxy.execute(bean -> secondChainValue.set(bean.getValue()));

        Assertions.assertEquals(0, secondChainValue.get(),
            "Each top-level chain must start with a fresh request bean");

        Assertions.assertEquals(2, ClassRequestPreDestroy.preDestroyCallCount.get(),
            "Each top-level chain must get a fresh request bean and destroy it at its end");
    }
}
