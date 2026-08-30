package de.oopexpert.oopdi;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.exception.CannotInject;
import de.oopexpert.oopdi.exception.NoRequestScopeAvailable;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

public class ProxyManager {

    private Map<Class<?>, Object> proxies = new HashMap<>();
    private Map<Class<?>, Class<?>> proxyClasses = new HashMap<>();

	public <B> B proxyIfNotExists(B instance) {
		return (B) proxyIfNotExists((Class<B>)instance.getClass(), c -> instance);
	}

	public <T> T proxyIfNotExists(Class<T> clazz, Function<Class<T>, T> realObjectCreator) {
		synchronized (proxies) {
			Class<T> nonProxyClass = nonProxyClazz(clazz);
			if (!proxies.containsKey(nonProxyClass)) {
				proxies.put(nonProxyClass, proxy(nonProxyClass, realObjectCreator));
			}
			return (T) proxies.get(nonProxyClass);
		}
	}

	private <T> T proxy(Class<T> clazz, Function<Class<T>, T> realObjectCreator) {

		java.lang.reflect.Constructor<?>[] constructors = clazz.getDeclaredConstructors();

		T proxiedObject;

        if (constructors.length == 0) {
            proxiedObject = createProxyWithDefaultConstructor(clazz, realObjectCreator);
            proxyClasses.put(proxiedObject.getClass(), clazz);
        } else if (constructors.length == 1) {
            proxiedObject = createProxyWithSingleConstructor(clazz, constructors[0], realObjectCreator);
            proxyClasses.put(proxiedObject.getClass(), clazz);
        } else {
            throw new CannotInject("Multiple constructors found in class '" + clazz.getName() + "'. Exactly one constructor is required for dependency injection.");
        }

		return proxiedObject;
	}

    private static final ThreadLocal<InstancesState> requestScope = new ThreadLocal<>();

	public static InstancesState getRequestScopedInstances() {
		InstancesState instanceState = requestScope.get();
		if (instanceState == null) {
			throw new NoRequestScopeAvailable();
		}
		return instanceState;
	}

	private <T> Class<? extends T> buildProxyClass(Class<T> clazz, Supplier<T> realObjectSupplier) {
		return new ByteBuddy()
			.subclass(clazz)
			.method(ElementMatchers.any())
			.intercept(InvocationHandlerAdapter.of(
				(proxy, method, args) -> intercept(method, args, realObjectSupplier)))
			.make()
			.load(clazz.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
			.getLoaded();
	}

	private <T> T createProxyWithDefaultConstructor(Class<T> clazz, Function<Class<T>, T> realObjectCreator) {
		try {
			Supplier<T> realObjectSupplier = buildRealObjectSupplier(clazz, realObjectCreator);
			return buildProxyClass(clazz, realObjectSupplier).getDeclaredConstructor().newInstance();
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			throw new CannotInject("Failed to instantiate proxy for '" + clazz.getName() + "'", new RuntimeException(e));
		}
	}

	private <T> T createProxyWithSingleConstructor(Class<T> clazz, java.lang.reflect.Constructor<?> constructor, Function<Class<T>, T> realObjectCreator) {
		try {
			Supplier<T> realObjectSupplier = buildRealObjectSupplier(clazz, realObjectCreator);
			Class<? extends T> proxyClass = buildProxyClass(clazz, realObjectSupplier);
			Object[] dummyArgs = argsForConstructor(constructor);
			return proxyClass.getDeclaredConstructor(constructor.getParameterTypes()).newInstance(dummyArgs);
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			throw new CannotInject("Failed to instantiate proxy for '" + clazz.getName() + "'", new RuntimeException(e));
		}
	}

	private <T> Supplier<T> buildRealObjectSupplier(Class<T> clazz, Function<Class<T>, T> realObjectCreator) {
		if (isImmediateInstantiationRequested(clazz) && Scope.isImmediateInstantiationPossible(clazz)) {
			T realObject = realObjectCreator.apply(clazz);
			return () -> realObject;
		}
		// GLOBAL and THREAD scoped beans resolve to a single stable real object (per container /
		// per thread respectively). Without immediate=true, resolving through realObjectCreator
		// on every single proxy method call re-runs annotation checks and lock acquisition for no
		// benefit once the instance exists, so cache it lazily instead. LOCAL (fresh instance per
		// call) and REQUEST (thread/call-depth scoped) must keep re-resolving on every call.
		switch (Scope.of(clazz)) {
			case GLOBAL:
				return globalCachingSupplier(clazz, realObjectCreator);
			case THREAD:
				return threadCachingSupplier(clazz, realObjectCreator);
			default:
				return () -> realObjectCreator.apply(clazz);
		}
	}

	private <T> Supplier<T> globalCachingSupplier(Class<T> clazz, Function<Class<T>, T> realObjectCreator) {
		AtomicReference<T> cache = new AtomicReference<>();
		Object lock = new Object();
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
		ThreadLocal<T> cache = new ThreadLocal<>();
		return () -> {
			T value = cache.get();
			if (value == null) {
				value = realObjectCreator.apply(clazz);
				cache.set(value);
			}
			return value;
		};
	}

	private <T> Object intercept(java.lang.reflect.Method method, Object[] args, Supplier<T> realObjectSupplier) throws Throwable {

        InstancesState instanceState = requestScope.get();

		if (instanceState == null) {
            instanceState = new InstancesState();
			requestScope.set(instanceState);
        }

		instanceState.incrementCallDepth();

        try {
        	return method.invoke(realObjectSupplier.get(), args);
        } finally {
    		instanceState.decrementCallDepth();
    		if (instanceState.getCallDepth() == 0) {
    			requestScope.remove();
    		}
        }
	}

    private static final Map<Class<?>, Object> PRIMITIVE_DEFAULTS = new HashMap<>();

    static {
        PRIMITIVE_DEFAULTS.put(boolean.class, false);
        PRIMITIVE_DEFAULTS.put(byte.class, (byte) 0);
        PRIMITIVE_DEFAULTS.put(char.class, (char) 0);
        PRIMITIVE_DEFAULTS.put(short.class, (short) 0);
        PRIMITIVE_DEFAULTS.put(int.class, 0);
        PRIMITIVE_DEFAULTS.put(long.class, 0L);
        PRIMITIVE_DEFAULTS.put(float.class, 0.0f);
        PRIMITIVE_DEFAULTS.put(double.class, 0.0d);
    }

    private Object[] argsForConstructor(java.lang.reflect.Constructor<?> constructor) {
        Class<?>[] paramTypes = constructor.getParameterTypes();
        Object[] args = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            args[i] = PRIMITIVE_DEFAULTS.getOrDefault(paramTypes[i], null);
        }
        return args;
    }

	private <A> Class<A> nonProxyClazz(Class<A> clazz) {
		Class<A> nonProxyClass = (Class<A>) proxyClasses.get(clazz);
		if (nonProxyClass == null) {
			nonProxyClass = clazz;
		}
		return nonProxyClass;
	}

	public static boolean isImmediateInstantiationRequested(Class<?> c) {
		return c.getAnnotation(Injectable.class).immediate();
	}

}
