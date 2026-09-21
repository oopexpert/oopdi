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

		return Scope.of(clazz).createSupplier(clazz, realObjectCreator);
	}

	public static boolean isImmediateInstantiationRequested(Class<?> c) {
		var injectable = c.getAnnotation(Injectable.class);
		return injectable != null && injectable.immediate();
	}
}