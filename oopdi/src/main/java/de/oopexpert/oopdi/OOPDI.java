package de.oopexpert.oopdi;

import java.util.Objects;

import de.oopexpert.oopdi.exception.ContainerNotStarted;
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
			"   ____    ____    ____    ____    ____\n"
			+ "  / __ \\  / __ \\  / __ \\  / __ \\  /  _/\n"
			+ " / / / / / / / / / /_/ / / / / /  / /\n"
			+ "/ /_/ / / /_/ / / ____/ / /_/ / _/ /\n"
			+ "\\____/  \\____/ /_/     /_____/ /___/\n";

	private final Class<T> rootClazz;
	private final String[] profiles;

	private ClasspathScanner warmupScanner;
	private RequestScopeManager requestScopeManager;
	private ScopedInstances scopedInstances;
	private MetadataMode metadataMode;
	private MetadataRepository metadataRepository;
	private ProxyManager proxyManager;
	private ClassesResolver classesResolver;
	private MetadataWarmup metadataWarmup;

	/**
	 * Never {@code null}: holds the {@link UninitializedContext} null object until
	 * {@link #startup(boolean)} replaces it with the real {@code Context}. Callers probe
	 * lifecycle state polymorphically (never {@code null}). Volatile for safe publication;
	 * only ever written inside the synchronized {@link #startup(boolean)}.
	 */
	private volatile Context<T> context;

	public OOPDI(Class<T> rootClazz, String... profiles) {
		this.rootClazz = Objects.requireNonNull(rootClazz, "rootClazz must not be null");
		this.profiles = Objects.requireNonNull(profiles, "profiles must not be null").clone();
		this.context = UninitializedContext.instance();
		// Startup banner goes to stdout (not the logger): an SLF4J backend is optional, so a
		// logger call could vanish silently for consumers without one.
		System.out.println(BANNER);
	}

	// Lazy, hierarchical component getters: each level only builds on the getters below it,
	// so the construction order mirrors the dependency hierarchy. All are synchronized on
	// this (reentrant), hence thread-safe and started at most once. Called from the
	// synchronized startup()/getContext()/shutdown() paths as well as the unsynchronized
	// getWarmupStatus().

	// One RequestScopeManager per container, shared by the interception path (ProxyManager)
	// and the instance-selection path (ScopedInstances): REQUEST-scoped state must never
	// leak across container boundaries on the same thread.
	private synchronized RequestScopeManager getRequestScopeManager() {
		if (requestScopeManager == null) {
			requestScopeManager = new RequestScopeManager();
		}
		return requestScopeManager;
	}

	private synchronized MetadataMode getMetadataMode() {
		if (metadataMode == null) {
			metadataMode = MetadataMode.fromSystemProperty();
		}
		return metadataMode;
	}

	private synchronized MetadataRepository getMetadataRepository() {
		if (metadataRepository == null) {
			metadataRepository = new MetadataRepository(getMetadataMode());
		}
		return metadataRepository;
	}

	private synchronized ScopedInstances getScopedInstances() {
		if (scopedInstances == null) {
			scopedInstances = new ScopedInstances(getRequestScopeManager());
		}
		return scopedInstances;
	}

	private synchronized ProxyManager getProxyManager() {
		if (proxyManager == null) {
			proxyManager = new ProxyManager(getRequestScopeManager(), getMetadataRepository());
		}
		return proxyManager;
	}

	private synchronized ClassesResolver getClassesResolver() {
		if (classesResolver == null) {
			classesResolver = new ClassesResolver(profiles.clone());
		}
		return classesResolver;
	}

	private synchronized ClasspathScanner getWarmupScanner() {
		if (warmupScanner == null) {
			warmupScanner = new ClasspathScanner();
		}
		return warmupScanner;
	}

	/**
	 * Never returns {@code null}: for warmup-disabled modes the shared
	 * {@link MetadataWarmup#disabled()} null object is cached and returned, so callers
	 * need no {@code null} branch. A {@code null} field means "not initialized yet" only.
	 */
	private synchronized MetadataWarmup getMetadataWarmup() {
		if (metadataWarmup == null) {
			if (!getMetadataMode().isWarmupEnabled()) {
				metadataWarmup = MetadataWarmup.disabled();
			} else {
				metadataWarmup = new MetadataWarmup(getWarmupScanner(), new InjectableFilter(profiles.clone()), getMetadataRepository(), getMetadataMode());
				metadataWarmup.start();
			}
		}
		return metadataWarmup;
	}

	/**
	 * Starts the container: runs the fail-fast warmup check and creates the real
	 * {@code Context}. Returns immediately while a background metadata warmup continues.
	 * Idempotent — a second call on a running container is a no-op; a call after shutdown
	 * fails fast with {@code ContainerShutdown} since a shut-down container cannot restart.
	 */
	public synchronized void startup() {
		startup(false);
	}

	/**
	 * Starts the container like {@link #startup()}, additionally blocking until startup is
	 * finished when {@code blockUntilReady} is {@code true}: "finished" means the background
	 * metadata warmup reached a terminal status ({@code READY} or {@code FAILED}), so a
	 * failed fail-fast warmup surfaces as {@code WarmupFailed} from this call. Returns
	 * immediately when no warmup is enabled.
	 */
	public synchronized void startup(boolean blockUntilReady) {
		if (!this.context.isUninitialized()) {
			if (this.context.getStatus() != ContainerStatus.ACTIVE) {
				throw new ContainerShutdown("Container has been shut down; it cannot be restarted.");
			}
			return;
		}
		MetadataWarmup warmup = getMetadataWarmup();
		if (blockUntilReady) {
			warmup.awaitCompletion();
		}
		checkWarmupNotFailedFast();
		this.context = new Context<>(this, rootClazz, getScopedInstances(), getProxyManager(), getClassesResolver(), getMetadataRepository());
	}

	synchronized Context<T> getContext() {
		if (this.context.isUninitialized()) {
			throw new ContainerNotStarted("Container has not been started; call startup() before requesting beans.");
		}
		checkWarmupNotFailedFast();
		return this.context;
	}

	private void checkWarmupNotFailedFast() {
		MetadataWarmup warmup = getMetadataWarmup();
		if (warmup.getStatus() == WarmupStatus.FAILED && getMetadataMode().isFailFast()) {
			throw new WarmupFailed("Background metadata warmup failed (classpath scan for @Injectable classes); switch to MetadataMode.WARMUP_LENIENT to keep operating via the on-demand fallback instead.",
					warmup.getFailureCause().orElse(null));
		}
	}

	/**
	 * Status of the background metadata warmup job. Always {@link WarmupStatus#NOT_STARTED} when
	 * {@link MetadataMode#isWarmupEnabled()} is {@code false} for the configured mode.
	 */
	public WarmupStatus getWarmupStatus() {
		return getMetadataWarmup().getStatus();
	}

	/**
	 * Status of the container lifecycle, mirroring {@link #getWarmupStatus()}.
	 * {@link ContainerStatus#NOT_STARTED} until {@link #startup(boolean)} is called; then
	 * {@link ContainerStatus#ACTIVE} until {@link #shutdown()} runs (one of the terminal
	 * states afterwards, or {@link ContainerStatus#SHUTTING_DOWN} while in progress). A
	 * shutdown before startup is neutral — there is nothing to destroy, so the status stays
	 * {@code NOT_STARTED}.
	 */
	public ContainerStatus getStatus() {
		return this.context.getStatus();
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

	/**
	 * Shuts the container down (idempotent; a second call is a no-op returning the terminal
	 * status). Neutral before {@link #startup(boolean)}: with nothing to destroy it does
	 * nothing and a later {@code startup()} still works.
	 */
	public synchronized void shutdown() {
		this.context.shutdown();
	}

	@Override
	public void close() {
		shutdown();
	}
}