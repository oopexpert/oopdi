package de.oopexpert.oopdi.proxy;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import de.oopexpert.oopdi.Scope;
import de.oopexpert.oopdi.annotation.Injectable;

public class ScopedSupplierFactory {

	public <T> Supplier<T> createSupplier(Class<T> clazz, Function<Class<T>, T> realObjectCreator) {
		Objects.requireNonNull(clazz, "clazz must not be null");
		Objects.requireNonNull(realObjectCreator, "realObjectCreator must not be null");

		if (isImmediateInstantiationRequested(clazz) && Scope.isImmediateInstantiationPossible(clazz)) {
			T realObject = realObjectCreator.apply(clazz);
			return () -> realObject;
		}

		return switch (Scope.of(clazz)) {
			case GLOBAL -> globalCachingSupplier(clazz, realObjectCreator);
			case THREAD -> threadCachingSupplier(clazz, realObjectCreator);
			default -> () -> realObjectCreator.apply(clazz);
		};
	}

	private <T> Supplier<T> globalCachingSupplier(Class<T> clazz, Function<Class<T>, T> realObjectCreator) {
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

	private <T> Supplier<T> threadCachingSupplier(Class<T> clazz, Function<Class<T>, T> realObjectCreator) {
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

	public static boolean isImmediateInstantiationRequested(Class<?> c) {
		var injectable = c.getAnnotation(Injectable.class);
		return injectable != null && injectable.immediate();
	}
}