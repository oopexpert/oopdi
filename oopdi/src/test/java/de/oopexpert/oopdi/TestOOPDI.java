package de.oopexpert.oopdi;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.teststructure.ClassA;
import de.oopexpert.teststructure.ClassB;
import de.oopexpert.teststructure.ClassB1;
import de.oopexpert.teststructure.ClassC;
import de.oopexpert.teststructure.ClassD;
import de.oopexpert.teststructure.ClassGlobalRace;
import de.oopexpert.teststructure.ClassMissingVar;
import de.oopexpert.teststructure.ClassOptionalVar;
import de.oopexpert.teststructure.ClassParallelA;
import de.oopexpert.teststructure.ClassParallelB;
import de.oopexpert.teststructure.ClassParallelInitTracker;
import de.oopexpert.teststructure.ClassPostConstructChild;
import de.oopexpert.teststructure.ClassPreDestroyChild;
import de.oopexpert.teststructure.ClassRequestScenario;
import de.oopexpert.teststructure.ClassRequestState;
import de.oopexpert.teststructure.ClassRoot;
import de.oopexpert.teststructure.ClassWithPreDestroy;

class TestOOPDI {
	
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

	
	@Test
	void testInjectSystemEnvironmentVariable() {
		
		OOPDI<ClassRoot> oopdi = new OOPDI<ClassRoot>(ClassRoot.class);
		
		String EXPECTED = "jdbc://mysql:userdb";
		
		ClassA instance = oopdi.getInstance(ClassA.class);

		Assertions.assertEquals(EXPECTED, instance.getDbURL());
		
	}

	@Test
	void testInjectParameterVariable() {
		
		OOPDI<ClassRoot> oopdi = new OOPDI<ClassRoot>(ClassRoot.class);
		
		String EXPECTED = "dbUser1";
		
		ClassA instance = oopdi.getInstance(ClassA.class);

		Assertions.assertEquals(EXPECTED, instance.getDbUsername());
		
	}

	@Test
	void testCommonTypeConversionToInt() {
		
		OOPDI<ClassRoot> oopdi = new OOPDI<ClassRoot>(ClassRoot.class);
		
		int EXPECTED = 4;
		
		ClassA instance = oopdi.getInstance(ClassA.class);

		Assertions.assertEquals(EXPECTED, instance.getCounter());
		
	}

	/**
	 * Demonstrates the GLOBAL scope check-then-act race condition in getOrCreateInjectable.
	 *
	 * Race window:
	 *   1. Thread 1 checks instanceExists() → false
	 *   2. Thread 1 enters createInstance(), acquires the scopedMap lock, runs the
	 *      constructor (which sleeps 200 ms while holding the lock)
	 *   3. Thread 2 checks instanceExists() → still false (Thread 1 hasn't called put() yet)
	 *   4. Thread 2 tries createInstance() → blocks on the scopedMap lock
	 *   5. Thread 1 wakes, releases lock, calls put(instance1)
	 *   6. Thread 2 acquires lock, creates instance2, calls put(instance2) — overwrites instance1
	 *
	 * NOTE: cglib calls the superclass constructor when creating the proxy object itself,
	 * so instanceCount is reset AFTER proxy creation to only count real-object constructions.
	 *
	 * Expected correct behaviour : instanceCount == 1 (one real object created)
	 * Actual buggy behaviour     : instanceCount == 2 (two real objects created)
	 *
	 * This test FAILS on the unfixed code to expose the bug.
	 */
	@Test
	void testGlobalScopeRaceConditionProducesDuplicateInstances() throws InterruptedException {

		CountDownLatch constructorStartedLatch = new CountDownLatch(1);

		OOPDI<ClassGlobalRace> oopdi = new OOPDI<>(ClassGlobalRace.class);
		ClassGlobalRace proxy = oopdi.getInstance(ClassGlobalRace.class);

		// Reset after proxy creation: cglib calls the superclass constructor when
		// building the proxy subclass, which would otherwise pollute the count.
		ClassGlobalRace.instanceCount.set(0);
		ClassGlobalRace.constructorStartedLatch = constructorStartedLatch;

		// Thread 1 triggers real object construction; its constructor signals when it
		// has entered (and is holding the scopedMap lock) then sleeps for 200 ms.
		Thread thread1 = new Thread(() -> proxy.getCount());
		thread1.start();

		// Wait until Thread 1 is inside the constructor (lock held, put() not yet called).
		constructorStartedLatch.await(5, TimeUnit.SECONDS);

		// Thread 2 now calls the proxy. instanceExists() is still false because Thread 1
		// has not yet called put(). Thread 2 passes the check, then blocks on the
		// scopedMap lock. When Thread 1 releases it, Thread 2 creates a second instance.
		Thread thread2 = new Thread(() -> proxy.getCount());
		thread2.start();

		thread1.join(5000);
		thread2.join(5000);

		// Correct behaviour: exactly 1 real object created.
		// Buggy behaviour: 2 real objects created — this assertion fails, exposing the race.
		Assertions.assertEquals(1, ClassGlobalRace.instanceCount.get(),
				"Race condition: both threads passed instanceExists=false before any put() " +
				"and each created a separate real object");
	}

	@Test
	void testInjectVariableMissingKeyThrowsDescriptiveError() {

		OOPDI<ClassMissingVar> oopdi = new OOPDI<>(ClassMissingVar.class);

		ClassMissingVar instance = oopdi.getInstance(ClassMissingVar.class);

		RuntimeException ex = Assertions.assertThrows(RuntimeException.class, instance::getMissingValue);

		Assertions.assertTrue(
			ex.getMessage().contains("definitelyNotSetKey_12345"),
			"Exception message should contain the missing key name, but was: " + ex.getMessage()
		);

	}

	@Test
	void testPostConstructInSuperclassIsInvoked() {

		OOPDI<ClassPostConstructChild> oopdi = new OOPDI<>(ClassPostConstructChild.class);

		ClassPostConstructChild instance = oopdi.getInstance(ClassPostConstructChild.class);

		Assertions.assertTrue(instance.isBaseInitialized(),
			"@PostConstruct method declared in abstract superclass should be invoked");

	}

	@Test
	void testInjectVariableOptionalYieldsNull() {

		OOPDI<ClassOptionalVar> oopdi = new OOPDI<>(ClassOptionalVar.class);

		ClassOptionalVar instance = oopdi.getInstance(ClassOptionalVar.class);

		Assertions.assertNull(instance.getOptionalValue(),
			"optional=true with missing key should inject null");

	}

	@Test
	void testInjectVariableDefaultValueUsedWhenKeyMissing() {

		OOPDI<ClassOptionalVar> oopdi = new OOPDI<>(ClassOptionalVar.class);

		ClassOptionalVar instance = oopdi.getInstance(ClassOptionalVar.class);

		Assertions.assertEquals("fallback", instance.getDefaultValue(),
			"defaultValue should be used when the key is not found");

	}

	@Test
	void testInjectVariableDefaultValueParsedForPrimitive() {

		OOPDI<ClassOptionalVar> oopdi = new OOPDI<>(ClassOptionalVar.class);

		ClassOptionalVar instance = oopdi.getInstance(ClassOptionalVar.class);

		Assertions.assertEquals(42, instance.getDefaultInt(),
			"defaultValue should be parsed to the field's primitive type");

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

	@Test
	void testRequestScopeNestedCallsShareSameRequestScopedState() {

		OOPDI<ClassRequestScenario> oopdi = new OOPDI<>(ClassRequestScenario.class);
		ClassRequestScenario scenario = oopdi.getInstance(ClassRequestScenario.class);

		// cglib may call constructors while creating proxies. Reset after obtaining the root proxy
		// so we only count real REQUEST-scoped instance creations.
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

		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(2);
		AtomicInteger idThreadOne = new AtomicInteger(-1);
		AtomicInteger idThreadTwo = new AtomicInteger(-1);

		Thread t1 = new Thread(() -> {
			try {
				start.await();
				ClassRequestScenario.Result result = scenario.execute(111, false);
				idThreadOne.set(result.getIdInScenario());
				Assertions.assertEquals(111, result.getReadFromReader());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				done.countDown();
			}
		});

		Thread t2 = new Thread(() -> {
			try {
				start.await();
				ClassRequestScenario.Result result = scenario.execute(222, false);
				idThreadTwo.set(result.getIdInScenario());
				Assertions.assertEquals(222, result.getReadFromReader());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				done.countDown();
			}
		});

		t1.start();
		t2.start();
		start.countDown();

		Assertions.assertTrue(done.await(5, TimeUnit.SECONDS), "Worker threads did not finish in time");
		Assertions.assertNotEquals(idThreadOne.get(), idThreadTwo.get(),
			"REQUEST scope instances must be isolated across threads");

	}

	@Test
	void testParallelInitializationForDifferentGlobalBeans() throws InterruptedException {

		OOPDI<ClassParallelA> oopdi = new OOPDI<>(ClassParallelA.class);
		ClassParallelA beanA = oopdi.getInstance(ClassParallelA.class);
		ClassParallelB beanB = oopdi.getInstance(ClassParallelB.class);

		// Enable tracking after proxy acquisition so cglib proxy constructor calls
		// do not pollute the observed real-initialization concurrency.
		ClassParallelInitTracker.resetAndEnable();

		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(2);

		Thread t1 = new Thread(() -> {
			try {
				start.await();
				beanA.touch();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				done.countDown();
			}
		});

		Thread t2 = new Thread(() -> {
			try {
				start.await();
				beanB.touch();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				done.countDown();
			}
		});

		t1.start();
		t2.start();
		start.countDown();

		Assertions.assertTrue(
			ClassParallelInitTracker.awaitBothStarted(5, TimeUnit.SECONDS),
			"Different GLOBAL beans should be allowed to initialize in parallel"
		);

		ClassParallelInitTracker.releaseConstructors();
		Assertions.assertTrue(done.await(5, TimeUnit.SECONDS), "Worker threads did not finish in time");
		ClassParallelInitTracker.disable();

		Assertions.assertTrue(
			ClassParallelInitTracker.getMaxConcurrentInits() >= 2,
			"Per-class lock granularity should allow parallel initialization of unrelated beans"
		);

	}

}
