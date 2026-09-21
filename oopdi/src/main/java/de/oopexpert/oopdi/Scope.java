package de.oopexpert.oopdi;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.proxy.RequestScopeManager;

public enum Scope {

	GLOBAL {
		@Override
		InstancesState select(InstancesState globalInstances, InstancesState threadInstances, RequestScopeManager requestScopeManager) {
			return globalInstances;
		}

		@Override
		public <T> Supplier<T> createSupplier(Class<T> clazz, Function<Class<T>, T> realObjectCreator) {
			return globalCachingSupplier(clazz, realObjectCreator);
		}

		@Override
		boolean isImmediateInstantiationPossible() {
			return true;
		}
	},
	THREAD {
		@Override
		InstancesState select(InstancesState globalInstances, InstancesState threadInstances, RequestScopeManager requestScopeManager) {
			return threadInstances;
		}

		@Override
		public <T> Supplier<T> createSupplier(Class<T> clazz, Function<Class<T>, T> realObjectCreator) {
			return threadCachingSupplier(clazz, realObjectCreator);
		}

		@Override
		boolean isImmediateInstantiationPossible() {
			return false;
		}
	},
	LOCAL {
		@Override
		InstancesState select(InstancesState globalInstances, InstancesState threadInstances, RequestScopeManager requestScopeManager) {
			return new InstancesState();
		}

		@Override
		public <T> Supplier<T> createSupplier(Class<T> clazz, Function<Class<T>, T> realObjectCreator) {
			return () -> realObjectCreator.apply(clazz);
		}

		@Override
		boolean isImmediateInstantiationPossible() {
			return false;
		}
	},
	REQUEST {
		@Override
		InstancesState select(InstancesState globalInstances, InstancesState threadInstances, RequestScopeManager requestScopeManager) {
			return requestScopeManager.getRequestScopedInstances();
		}

		@Override
		public <T> Supplier<T> createSupplier(Class<T> clazz, Function<Class<T>, T> realObjectCreator) {
			return () -> realObjectCreator.apply(clazz);
		}

		@Override
		boolean isImmediateInstantiationPossible() {
			return false;
		}
	};

	abstract InstancesState select(InstancesState globalInstances, InstancesState threadInstances, RequestScopeManager requestScopeManager);

	/**
	 * Creates the real-object supplier for the given bean class, polymorphically per scope
	 * (no switch statements): GLOBAL caches process-wide, THREAD caches per thread, LOCAL and
	 * REQUEST re-resolve on every call (LOCAL needs a fresh instance per call; REQUEST is
	 * thread/call-depth scoped via the request manager).
	 */
	public abstract <T> Supplier<T> createSupplier(Class<T> clazz, Function<Class<T>, T> realObjectCreator);
	
	abstract boolean isImmediateInstantiationPossible();
	
	public static <X> Scope of(Class<X> c) {
		return c.getAnnotation(Injectable.class).scope();
	}
	
	public static boolean isImmediateInstantiationPossible(Class<?> clazz) {
		return of(clazz).isImmediateInstantiationPossible();
	}

	private static <T> Supplier<T> globalCachingSupplier(Class<T> clazz, Function<Class<T>, T> realObjectCreator) {
		var cache = new AtomicReference<T>();
		var lock = new Object();
		return () -> {
			T value = cache.get();
			if (value == null) {
				synchronized (lock) {
					value = cache.get();
					if (value == null) {
						value = realObjectCreator.apply(clazz);
						cache.set(value);
					}
				}
			}
			return value;
		};
	}

	private static <T> Supplier<T> threadCachingSupplier(Class<T> clazz, Function<Class<T>, T> realObjectCreator) {
		var cache = new ThreadLocal<T>();
		return () -> {
			T value = cache.get();
			if (value == null) {
				value = realObjectCreator.apply(clazz);
				cache.set(value);
			}
			return value;
		};
	}

}
