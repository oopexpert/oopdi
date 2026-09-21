package de.oopexpert.oopdi;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import de.oopexpert.oopdi.exception.CannotInject;
import de.oopexpert.oopdi.exception.ContainerShutdown;
import de.oopexpert.oopdi.metadata.ClassMetadata;
import de.oopexpert.oopdi.metadata.MetadataRepository;
import de.oopexpert.oopdi.resolver.DependencyResolutionContext;
import de.oopexpert.oopdi.resolver.DependencyResolverPipeline;
import de.oopexpert.oopdi.resolver.FieldInjectionPoint;
import de.oopexpert.oopdi.resolver.InjectionPoint;
import de.oopexpert.oopdi.resolver.InternalResolutionContext;
import de.oopexpert.oopdi.resolver.impl.InstanceDependencyResolver;
import de.oopexpert.oopdi.resolver.impl.SetDependencyResolver;
import de.oopexpert.oopdi.resolver.impl.VariableDependencyResolver;

public class Context<T> implements InternalResolutionContext {

	private final ScopedInstances scopedInstances;
	private final ProxyManager proxyManager;
	private final DependencyResolverPipeline resolverPipeline;
	private final InstanceFactory instanceFactory;
	private final LifecycleProcessor lifecycleProcessor;
	private final MetadataRepository metadataRepository;

	private final ThreadLocal<Boolean> directConstructionPhase = ThreadLocal.withInitial(() -> false);

	private final AtomicReference<ShutdownStatus> shutdownStatus = new AtomicReference<>(ShutdownStatus.ACTIVE);

	public Context(OOPDI<T> oopdi, Class<T> rootClazz, ScopedInstances scopedInstances,
			ProxyManager proxyManager, ClassesResolver classesResolver, MetadataRepository metadataRepository) {
		this.scopedInstances = Objects.requireNonNull(scopedInstances);
		this.proxyManager = Objects.requireNonNull(proxyManager);
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository must not be null");

		this.resolverPipeline = new DependencyResolverPipeline(List.of(
				new VariableDependencyResolver(),
				new SetDependencyResolver(classesResolver),
				new InstanceDependencyResolver()
		));

		this.lifecycleProcessor = new LifecycleProcessor(this, oopdi, metadataRepository);
		this.instanceFactory = new InstanceFactory(this, classesResolver, oopdi, metadataRepository);

		// Request-scoped beans die with their call chain: wire the lifecycle processor as the
		// request-end destroyer (set after construction to avoid a wiring cycle; the volatile
		// Context publication in OOPDI makes this safely visible).
		this.proxyManager.getRequestScopeManager().setRequestEndDestroyer(lifecycleProcessor::invokePreDestroy);

		this.proxyManager.proxyIfNotExists(rootClazz, instanceFactory::validateEligible, this::getOrCreate);
	}

	@Override
	public <A> A getOrCreate(Class<A> clazz) {
		checkNotShuttingDown();
		instanceFactory.validateEligible(clazz);
		instanceFactory.checkImmediateInstantiationConfiguration(clazz);
		return instanceFactory.getOrCreateInjectable(clazz, scopedInstances, instance -> {
			injectFields(instance);
			lifecycleProcessor.executePostConstruct(instance);
		}, directConstructionPhase);
	}

	/**
	 * The single guard against bean creation once shutdown has started. Every real object the
	 * framework creates funnels through {@link #getOrCreate(Class)} (top-level lazy resolution,
	 * eager {@code immediate} instantiation, and all nested constructor/field/set/lifecycle
	 * resolutions), so one check here covers all of them: requests arriving after shutdown
	 * began fail fast with {@link ContainerShutdown} instead of producing instances that could
	 * never be destroyed again.
	 */
	private void checkNotShuttingDown() {
		if (shutdownStatus.get() != ShutdownStatus.ACTIVE) {
			throw new ContainerShutdown("Container is shutting down or has been shut down; "
					+ "no new beans can be created (status: " + shutdownStatus.get() + ").");
		}
	}


	@Override
	public <A> A getOrCreateProxy(Class<A> clazz) {
		return proxyManager.proxyIfNotExists(clazz, instanceFactory::validateEligible, this::getOrCreate);
	}

	@Override
	public boolean isDirectConstructionPhase() {
		return Boolean.TRUE.equals(directConstructionPhase.get());
	}

	public void injectFields(Object instance) {
		ClassMetadata metadata = metadataRepository.getMetadata(instance.getClass());
		for (InjectionPoint point : metadata.getFieldInjectionPoints()) {
			if (resolverPipeline.supports(point)) {
				Field field = ((FieldInjectionPoint) point).field();
				field.setAccessible(true);
				Object resolvedValue = resolverPipeline.resolve(point, this);
				try {
					field.set(instance, resolvedValue);
				} catch (IllegalAccessException e) {
					throw new CannotInject("Feldinjektion fehlgeschlagen: " + field.getName(), e);
				}
			}
		}
	}

	public ShutdownStatus getShutdownStatus() {
		return shutdownStatus.get();
	}

	/**
	 * Shuts the container down: destroys every known instance in reverse creation order per
	 * scope, best-effort. A failing {@code @PreDestroy} method does not abort the shutdown —
	 * destruction continues with all remaining instances and the individual failures are
	 * aggregated as suppressed exceptions on the thrown error (terminal status
	 * {@link ShutdownStatus#FAILED} instead of {@link ShutdownStatus#SHUTDOWN}).
	 *
	 * <p>Only one thread performs the shutdown (compare-and-set from {@code ACTIVE} to
	 * {@code SHUTTING_DOWN}); concurrent or repeated calls are no-ops returning the terminal
	 * status. Since in-flight creations cannot be aborted (Java has no safe thread abortion),
	 * destruction runs as a drain loop: snapshots are repeated until a pass finds no
	 * not-yet-destroyed instances. This terminates because {@link #checkNotShuttingDown()}
	 * rejects every new request once shutdown has started, so only the already-running,
	 * finite chains can still add instances.</p>
	 */
	public void shutdown() {
		if (!shutdownStatus.compareAndSet(ShutdownStatus.ACTIVE, ShutdownStatus.SHUTTING_DOWN)) {
			return;
		}
		List<Throwable> failures = new ArrayList<>();
		Set<Object> destroyed = Collections.newSetFromMap(new IdentityHashMap<>());
		try {
			boolean progress;
			do {
				progress = false;
				for (InstancesState state : scopedInstances.allInstanceStates()) {
					for (Object instance : state.allInstancesInReverseCreationOrder()) {
						if (destroyed.add(instance)) {
							progress = true;
							try {
								lifecycleProcessor.invokePreDestroy(instance);
							} catch (RuntimeException e) {
								failures.add(e);
							}
						}
					}
				}
			} while (progress);
		} finally {
			shutdownStatus.set(failures.isEmpty() ? ShutdownStatus.SHUTDOWN : ShutdownStatus.FAILED);
		}
		if (!failures.isEmpty()) {
			RuntimeException aggregated = new RuntimeException("Shutdown completed with "
					+ failures.size() + " failing @PreDestroy invocation(s); "
					+ "all remaining instances were still destroyed best-effort.");
			failures.forEach(aggregated::addSuppressed);
			throw aggregated;
		}
	}
}