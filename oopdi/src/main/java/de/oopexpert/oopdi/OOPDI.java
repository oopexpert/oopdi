package de.oopexpert.oopdi;

import java.util.Objects;

import de.oopexpert.oopdi.exception.WarmupFailed;
import de.oopexpert.oopdi.metadata.MetadataMode;
import de.oopexpert.oopdi.metadata.MetadataRepository;
import de.oopexpert.oopdi.metadata.MetadataWarmup;
import de.oopexpert.oopdi.metadata.WarmupStatus;

public class OOPDI<T> implements AutoCloseable {

	private final ScopedInstances scopedInstances;
	private final Class<T> rootClazz;
	private final ProxyManager proxyManager;
	private final ClassesResolver classesResolver;
	private final MetadataRepository metadataRepository;
	private final MetadataMode metadataMode;
	private final MetadataWarmup metadataWarmup;

	private volatile Context<T> context;

	public OOPDI(Class<T> rootClazz, String... profiles) {
		this(rootClazz, new ClasspathScanner(), profiles);
	}

	/**
	 * Package-private test seam: allows tests to inject a (possibly failure-simulating)
	 * {@link ClasspathScanner} for the background metadata warmup, without exposing that as
	 * public API.
	 */
	OOPDI(Class<T> rootClazz, ClasspathScanner warmupScanner, String... profiles) {
		this.rootClazz = Objects.requireNonNull(rootClazz, "rootClazz must not be null");
		this.scopedInstances = new ScopedInstances();
		this.metadataMode = MetadataMode.fromSystemProperty();
		this.metadataRepository = new MetadataRepository(metadataMode);
		this.proxyManager = new ProxyManager(metadataRepository);
		this.classesResolver = new ClassesResolver(profiles);

		if (metadataMode.isWarmupEnabled()) {
			this.metadataWarmup = new MetadataWarmup(warmupScanner, new InjectableFilter(profiles), metadataRepository, metadataMode);
			this.metadataWarmup.start();
		} else {
			this.metadataWarmup = null;
		}
	}

	synchronized Context<T> getContext() {
		checkWarmupNotFailedFast();
		if (this.context == null) {
			this.context = new Context<>(this, rootClazz, scopedInstances, proxyManager, classesResolver, metadataRepository);
		}
		return this.context;
	}

	private void checkWarmupNotFailedFast() {
		if (metadataWarmup != null && metadataMode.isFailFast() && metadataWarmup.getStatus() == WarmupStatus.FAILED) {
			throw new WarmupFailed("Background metadata warmup failed (classpath scan for @Injectable classes); "
					+ "switch to MetadataMode.WARMUP_LENIENT to keep operating via the on-demand fallback instead.",
					metadataWarmup.getFailureCause().orElse(null));
		}
	}

	/**
	 * Status of the background metadata warmup job. Always {@link WarmupStatus#NOT_STARTED} when
	 * {@link MetadataMode#isWarmupEnabled()} is {@code false} for the configured mode.
	 */
	public WarmupStatus getWarmupStatus() {
		return metadataWarmup != null ? metadataWarmup.getStatus() : WarmupStatus.NOT_STARTED;
	}

	/**
	 * Status of the container shutdown, mirroring {@link #getWarmupStatus()}. Always
	 * {@link ShutdownStatus#ACTIVE} until {@link #shutdown()} is called; afterwards one of the
	 * terminal states (or {@link ShutdownStatus#SHUTTING_DOWN} while it is in progress).
	 */
	public ShutdownStatus getShutdownStatus() {
		return this.context != null ? this.context.getShutdownStatus() : ShutdownStatus.ACTIVE;
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