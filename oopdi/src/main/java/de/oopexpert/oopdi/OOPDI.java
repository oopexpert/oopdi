package de.oopexpert.oopdi;

import java.util.Objects;

public class OOPDI<T> implements AutoCloseable {

	private final ScopedInstances scopedInstances;
	private final Class<T> rootClazz;
	private final ProxyManager proxyManager;
	private final ClassesResolver classesResolver;

	private volatile Context<T> context;

	public OOPDI(Class<T> rootClazz, String... profiles) {
		this.rootClazz = Objects.requireNonNull(rootClazz, "rootClazz must not be null");
		this.scopedInstances = new ScopedInstances();
		this.proxyManager = new ProxyManager();
		this.classesResolver = new ClassesResolver(profiles);
	}

	synchronized Context<T> getContext() {
		if (this.context == null) {
			this.context = new Context<>(this, rootClazz, scopedInstances, proxyManager, classesResolver);
		}
		return this.context;
	}

	public <X> X getInstance(Class<X> clazz) {
		return getContext().getOrCreateProxy(clazz);
	}
	
	public void shutdown() {
		if (this.context != null) {
			this.context.shutdown();
		}
	}

	@Override
	public void close() {
		shutdown();
	}
}