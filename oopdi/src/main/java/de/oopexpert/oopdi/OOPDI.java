package de.oopexpert.oopdi;

import java.util.Objects;

import de.oopexpert.oopdi.exception.ContainerShutdown;
import de.oopexpert.oopdi.exception.WarmupFailed;
import de.oopexpert.oopdi.metadata.MetadataMode;
import de.oopexpert.oopdi.metadata.MetadataRepository;
import de.oopexpert.oopdi.metadata.MetadataWarmup;
import de.oopexpert.oopdi.metadata.WarmupStatus;
import de.oopexpert.oopdi.proxy.RequestScopeManager;

public class OOPDI<T> implements AutoCloseable {

	// Built from explicit literals (not a text block): text blocks derive their
	// indentation from the closing delimiter, which silently garbles ASCII art on
	// mixed tabs/spaces. Each line below matches README.md byte-for-byte.
	private static final String BANNER =
			"____    ____    ____    ____    ____\n"
			+ "  / __ \\  / __ \\  / __ \\  / __ \\  /  _/\n"
			+ " / / / / / / / / / /_/ / / / / /  / /\n"
			+ "/ /_/ / / /_/ / / ____/ / /_/ / _/ /\n"
			+ "\\____/  \\____/ /_/     /_____/ /___/\n";

	private final ScopedInstances scopedInstances;
	private final Class<T> rootClazz;
	private final ProxyManager proxyManager;
	private final ClassesResolver classesResolver;
	private final MetadataRepository metadataRepository;
	private final MetadataMode metadataMode;
	private final MetadataWarmup metadataWarmup;

	private volatile Context<T> context;

	/**
	 * Remembers a shutdown request that arrived before any {@code Context} existed. Without
	 * this, {@link #shutdown()} on a never-used container would silently do nothing and a
	 * later {@link #getInstance(Class)} would serve beans from a container that was already
	 * shut down. Volatile for safe publication; only ever written inside the synchronized
	 * {@link #shutdown()} and read inside the synchronized {@link #getContext()}.
	 */
	private volatile boolean shutdownRequested;

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
		// One RequestScopeManager per container, shared by the interception path (ProxyManager)
		// and the instance-selection path (ScopedInstances): REQUEST-scoped state must never
		// leak across container boundaries on the same thread.
		RequestScopeManager requestScopeManager = new RequestScopeManager();
		this.scopedInstances = new ScopedInstances(requestScopeManager);
		this.metadataMode = MetadataMode.fromSystemProperty();
		this.metadataRepository = new MetadataRepository(metadataMode);
		this.proxyManager = new ProxyManager(requestScopeManager, metadataRepository);
		this.classesResolver = new ClassesResolver(profiles);

		if (metadataMode.isWarmupEnabled()) {
			this.metadataWarmup = new MetadataWarmup(warmupScanner, new InjectableFilter(profiles), metadataRepository, metadataMode);
			this.metadataWarmup.start();
		} else {
			this.metadataWarmup = null;
		}
		// Startup banner goes to stdout (not the logger): an SLF4J backend is optional, so a
		// logger call could vanish silently for consumers without one.
		System.out.println(BANNER);
	}

	synchronized Context<T> getContext() {
		checkWarmupNotFailedFast();
		if (this.context == null) {
			if (shutdownRequested) {
				throw new ContainerShutdown("Container has been shut down before its first use; no beans can be created.");
			}
			this.context = new Context<>(this, rootClazz, scopedInstances, proxyManager, classesResolver, metadataRepository);
		}
		return this.context;
	}

	private void checkWarmupNotFailedFast() {
		if (metadataWarmup != null && metadataMode.isFailFast() && metadataWarmup.getStatus() == WarmupStatus.FAILED) {
			throw new WarmupFailed("Background metadata warmup failed (classpath scan for @Injectable classes); switch to MetadataMode.WARMUP_LENIENT to keep operating via the on-demand fallback instead.",
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
	 * terminal states (or {@link ShutdownStatus#SHUTTING_DOWN} while it is in progress). A
	 * shutdown requested before first use (no {@code Context} exists yet) reports
	 * {@link ShutdownStatus#SHUTDOWN} — there is nothing to destroy.
	 */
	public ShutdownStatus getShutdownStatus() {
		if (this.context != null) {
			return this.context.getShutdownStatus();
		}
		return shutdownRequested ? ShutdownStatus.SHUTDOWN : ShutdownStatus.ACTIVE;
	}

	public <X> X getInstance(Class<X> clazz) {
		return getContext().getOrCreateProxy(clazz);
	}

	/**
	 * Dry-validates the bean graph reachable from this container's root class without creating
	 * a single instance: no constructor runs, no field is set, no lifecycle method fires.
	 * Structural wiring problems are aggregated into one {@code CannotInject}; a silent return
	 * means the graph would resolve at runtime. Opt-in — call explicitly at application boot.
	 * Uses this container's active profiles, so validation reflects exactly what resolution
	 * would see.
	 */
	public void validate() {
		getContext().validateGraph(rootClazz);
	}

	public synchronized void shutdown() {
		shutdownRequested = true;
		if (this.context != null) {
			this.context.shutdown();
		}
	}

	@Override
	public void close() {
		shutdown();
	}
}