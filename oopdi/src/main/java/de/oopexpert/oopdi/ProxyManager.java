package de.oopexpert.oopdi;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import de.oopexpert.oopdi.metadata.MetadataRepository;
import de.oopexpert.oopdi.proxy.ByteBuddyProxyFactory;
import de.oopexpert.oopdi.proxy.RequestScopeManager;
import de.oopexpert.oopdi.proxy.ScopedSupplierFactory;

public class ProxyManager {

	private final Map<Class<?>, Object> proxies = new HashMap<>();
	private final Map<Class<?>, Class<?>> proxyClasses = new HashMap<>();

	private final ByteBuddyProxyFactory proxyFactory;
	private final ScopedSupplierFactory supplierFactory;

	public ProxyManager(RequestScopeManager requestScopeManager, MetadataRepository metadataRepository) {
		this(requestScopeManager, new ScopedSupplierFactory(), metadataRepository);
	}

	public ProxyManager(RequestScopeManager requestScopeManager, ScopedSupplierFactory supplierFactory,
			MetadataRepository metadataRepository) {
		Objects.requireNonNull(requestScopeManager, "requestScopeManager must not be null");
		this.supplierFactory = Objects.requireNonNull(supplierFactory, "supplierFactory must not be null");
		this.proxyFactory = new ByteBuddyProxyFactory(requestScopeManager, metadataRepository);
	}

	public <B> B proxyIfNotExists(B instance) {
		@SuppressWarnings("unchecked")
		Class<B> clazz = (Class<B>) instance.getClass();
		return proxyIfNotExists(clazz, c -> instance);
	}

	public <T> T proxyIfNotExists(Class<T> clazz, Function<Class<T>, T> realObjectCreator) {
		return proxyIfNotExists(clazz, c -> { }, realObjectCreator);
	}

	public <T> T proxyIfNotExists(Class<T> clazz, Consumer<Class<T>> eligibilityCheck, Function<Class<T>, T> realObjectCreator) {
		synchronized (proxies) {
			Class<T> nonProxyClass = nonProxyClazz(clazz);
			if (!proxies.containsKey(nonProxyClass)) {
				eligibilityCheck.accept(nonProxyClass);
				Supplier<T> realObjectSupplier = supplierFactory.createSupplier(nonProxyClass, realObjectCreator);
				T proxiedObject = proxyFactory.createProxy(nonProxyClass, realObjectSupplier);

				proxyClasses.put(proxiedObject.getClass(), nonProxyClass);
				proxies.put(nonProxyClass, proxiedObject);
			}
			@SuppressWarnings("unchecked")
			T existing = (T) proxies.get(nonProxyClass);
			return existing;
		}
	}

	@SuppressWarnings("unchecked")
	public <A> Class<A> nonProxyClazz(Class<A> clazz) {
		Class<A> nonProxyClass = (Class<A>) proxyClasses.get(clazz);
		return nonProxyClass != null ? nonProxyClass : clazz;
	}

	public static boolean isImmediateInstantiationRequested(Class<?> c) {
		return ScopedSupplierFactory.isImmediateInstantiationRequested(c);
	}
}