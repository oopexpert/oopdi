package de.oopexpert.oopdi.proxy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import de.oopexpert.oopdi.exception.CannotInject;
import de.oopexpert.oopdi.metadata.MetadataRepository;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FieldAccessor;
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

	/**
	 * Generated proxy classes, shared across containers: a proxy class carries no
	 * container-specific state (the per-proxy {@link ProxyTarget} is held in instance fields
	 * and read through {@link ProxiedBeanCarrier}), so each bean class is generated and loaded
	 * only once per JVM instead of once per container. This saves startup time and metaspace
	 * for applications (and test suites) creating many containers.
	 *
	 * <p>Known limitation: entries pin both the bean class and the generated class for the
	 * lifetime of the JVM — fine for applications with a bounded set of beans, unsuitable for
	 * environments that hot-redeploy large numbers of classes (same trade-off as comparable
	 * framework caches).</p>
	 */
	private static final ConcurrentHashMap<Class<?>, Class<?>> PROXY_CLASSES = new ConcurrentHashMap<>();

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

		T proxy;
		if (primaryConstructor == null) {
			proxy = instantiateViaSharedClass(clazz, new Class<?>[0], new Object[0]);
		} else {
			proxy = instantiateViaSharedClass(clazz, primaryConstructor.getParameterTypes(),
					argsForConstructor(primaryConstructor));
		}
		((ProxiedBeanCarrier) proxy).oopdi$init(new ProxyTarget(requestScopeManager, realObjectSupplier));
		return proxy;
	}

	@SuppressWarnings("unchecked")
	private <T> T instantiateViaSharedClass(Class<T> clazz, Class<?>[] parameterTypes, Object[] args) {
		try {
			Class<? extends T> proxyClass = (Class<? extends T>) PROXY_CLASSES.computeIfAbsent(clazz, this::buildProxyClass);
			return proxyClass.getDeclaredConstructor(parameterTypes).newInstance(args);
			// IllegalArgumentException included deliberately: ByteBuddy rejects what it cannot
			// subclass (e.g. final classes) with an unchecked error during class generation;
			// surface it as a descriptive CannotInject like every other proxy-creation failure.
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | IllegalArgumentException e) {
			throw new CannotInject("Failed to instantiate proxy for '%s'.".formatted(clazz.getName()), e);
		}
	}

	private Class<?> buildProxyClass(Class<?> clazz) {
		return new ByteBuddy()
				.subclass(clazz)
				.implement(ProxiedBeanCarrier.class)
				.defineField("oopdi$target", ProxyTarget.class, Visibility.PRIVATE)
				.method(ElementMatchers.isDeclaredBy(ProxiedBeanCarrier.class)
						.and(ElementMatchers.named("oopdi$init")))
				.intercept(FieldAccessor.ofField("oopdi$target").setsArgumentAt(0))
				.method(ElementMatchers.isDeclaredBy(ProxiedBeanCarrier.class)
						.and(ElementMatchers.named("oopdi$target")))
				.intercept(FieldAccessor.ofField("oopdi$target"))
				.method(ElementMatchers.any()
						.and(ElementMatchers.not(ElementMatchers.isDeclaredBy(ProxiedBeanCarrier.class))))
				.intercept(InvocationHandlerAdapter.of(
						(proxy, method, args) -> dispatch((ProxiedBeanCarrier) proxy, method, args)))
				.make()
				.load(clazz.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
				.getLoaded();
	}

	@SuppressWarnings("unchecked")
	private static Object dispatch(ProxiedBeanCarrier carrier, java.lang.reflect.Method method, Object[] args) throws Throwable {
		ProxyTarget target = carrier.oopdi$target();
		return target.requestScopeManager().intercept(method, args, (Supplier<Object>) target.realObjectSupplier());
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