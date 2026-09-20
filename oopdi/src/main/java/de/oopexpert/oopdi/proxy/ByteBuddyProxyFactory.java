package de.oopexpert.oopdi.proxy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import de.oopexpert.oopdi.exception.CannotInject;
import de.oopexpert.oopdi.metadata.MetadataRepository;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

public class ByteBuddyProxyFactory {

	private static final Map<Class<?>, Object> PRIMITIVE_DEFAULTS = Map.of(
			boolean.class, false,
			byte.class, (byte) 0,
			char.class, (char) 0,
			short.class, (short) 0,
			int.class, 0,
			long.class, 0L,
			float.class, 0.0f,
			double.class, 0.0d
	);

	private final RequestScopeManager requestScopeManager;
	private final MetadataRepository metadataRepository;

	public ByteBuddyProxyFactory(RequestScopeManager requestScopeManager, MetadataRepository metadataRepository) {
		this.requestScopeManager = Objects.requireNonNull(requestScopeManager, "requestScopeManager must not be null");
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository must not be null");
	}

	/**
	 * The primary constructor (which one to mimic for the proxy stub) is exclusively determined
	 * by {@link MetadataRepository}, which is also the single place validating the "exactly one
	 * constructor" invariant (throws {@link de.oopexpert.oopdi.exception.MultipleConstructors}).
	 * This factory must not re-derive or re-validate that itself.
	 */
	public <T> T createProxy(Class<T> clazz, Supplier<T> realObjectSupplier) {
		Constructor<?> primaryConstructor = metadataRepository.getMetadata(clazz).getPrimaryConstructor();

		if (primaryConstructor == null) {
			return createProxyWithDefaultConstructor(clazz, realObjectSupplier);
		}
		return createProxyWithSingleConstructor(clazz, primaryConstructor, realObjectSupplier);
	}

	private <T> T createProxyWithDefaultConstructor(Class<T> clazz, Supplier<T> realObjectSupplier) {
		try {
			Class<? extends T> proxyClass = buildProxyClass(clazz, realObjectSupplier);
			return proxyClass.getDeclaredConstructor().newInstance();
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			throw new CannotInject("Failed to instantiate proxy for '" + clazz.getName() + "'", e);
		}
	}

	private <T> T createProxyWithSingleConstructor(Class<T> clazz, Constructor<?> constructor, Supplier<T> realObjectSupplier) {
		try {
			Class<? extends T> proxyClass = buildProxyClass(clazz, realObjectSupplier);
			Object[] dummyArgs = argsForConstructor(constructor);
			return proxyClass.getDeclaredConstructor(constructor.getParameterTypes()).newInstance(dummyArgs);
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			throw new CannotInject("Failed to instantiate proxy for '" + clazz.getName() + "'", e);
		}
	}

	private <T> Class<? extends T> buildProxyClass(Class<T> clazz, Supplier<T> realObjectSupplier) {
		return new ByteBuddy()
				.subclass(clazz)
				.method(ElementMatchers.any())
				.intercept(InvocationHandlerAdapter.of(
						(proxy, method, args) -> requestScopeManager.intercept(method, args, realObjectSupplier)))
				.make()
				.load(clazz.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
				.getLoaded();
	}

	private Object[] argsForConstructor(Constructor<?> constructor) {
		Class<?>[] paramTypes = constructor.getParameterTypes();
		Object[] args = new Object[paramTypes.length];
		for (int i = 0; i < paramTypes.length; i++) {
			args[i] = PRIMITIVE_DEFAULTS.getOrDefault(paramTypes[i], null);
		}
		return args;
	}
}