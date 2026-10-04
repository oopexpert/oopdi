package de.oopexpert.oopdi;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.exception.AbstractBean;
import de.oopexpert.oopdi.exception.BeanInstantiationFailed;
import de.oopexpert.oopdi.exception.ConstructorCycle;
import de.oopexpert.oopdi.exception.ContainerShutdown;
import de.oopexpert.oopdi.exception.ImmediateScopeMisconfiguration;
import de.oopexpert.oopdi.exception.NoAccessibleConstructor;
import de.oopexpert.oopdi.exception.NotInjectableBean;
import de.oopexpert.oopdi.exception.UnderConstruction;
import de.oopexpert.oopdi.metadata.ClassMetadata;
import de.oopexpert.oopdi.metadata.MetadataRepository;
import de.oopexpert.oopdi.resolver.DependencyResolutionContext;

public class InstanceFactory {

	private static final Logger log = LoggerFactory.getLogger(InstanceFactory.class);

	private final DependencyResolutionContext context;
	private final ClassesResolver classesResolver;
	private final OOPDI<?> oopdi;
	private final MetadataRepository metadataRepository;
	private final Supplier<ContainerStatus> shutdownState;
	private final Consumer<Object> immediateDestroyer;

	public InstanceFactory(DependencyResolutionContext context, ClassesResolver classesResolver, OOPDI<?> oopdi, MetadataRepository metadataRepository,
			Supplier<ContainerStatus> shutdownState, Consumer<Object> immediateDestroyer) {
		this.context = Objects.requireNonNull(context, "context must not be null");
		this.classesResolver = Objects.requireNonNull(classesResolver, "classesResolver must not be null");
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository must not be null");
		this.shutdownState = Objects.requireNonNull(shutdownState, "shutdownState must not be null");
		this.immediateDestroyer = Objects.requireNonNull(immediateDestroyer, "immediateDestroyer must not be null");
		this.oopdi = oopdi;
	}
	
	public <A> void validateEligible(Class<A> c) {
		checkInjectableAnnotated(c);
		checkNonAbstract(c);
	}

	public <A> void checkImmediateInstantiationConfiguration(Class<A> c) {
		if (ProxyManager.isImmediateInstantiationRequested(c) && !Scope.isImmediateInstantiationPossible(c)) {
			throw new ImmediateScopeMisconfiguration("Misconfiguration of class '%s': it is configured to be instantiated immediately, but this is only possible with scope GLOBAL.".formatted(c.getName()));
		}
	}

	public <X> X getOrCreateInjectable(Class<X> x, ScopedInstances scopedInstances, Consumer<Object> postProcessor, ThreadLocal<Boolean> directConstructionPhase) {
		try {
			Class<X> c = determineRelevantClass(x);
			InstancesState scopedMap = scopedInstances.getScopedInstancesState(Scope.of(c));
			return lockedGetOrCreate(c, scopedMap, postProcessor, directConstructionPhase);
		} catch (RuntimeException re) {
			// Load-bearing passthrough (not dead code): without this branch, runtime
			// exceptions would fall into the Exception handler below and be wrongly
			// wrapped. Do not "simplify" away.
			throw re;
		} catch (Exception e) {
			throw new BeanInstantiationFailed("Failed to resolve bean for '%s'.".formatted(x.getName()), e);
		}
	}

	@SuppressWarnings("unchecked")
	private <X> Class<X> determineRelevantClass(Class<X> x) {
		return (Class<X>) classesResolver.determineRelevantClass(x);
	}

	private <X> X lockedGetOrCreate(Class<X> c, InstancesState scopedMap, Consumer<Object> postProcessor, ThreadLocal<Boolean> directConstructionPhase) {
		synchronized (scopedMap.getLockFor(c)) {
			if (scopedMap.instanceExists(c)) {
				return existingInstance(c, scopedMap);
			} else {
				return createPostProcessedInstance(c, scopedMap, postProcessor, directConstructionPhase);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private <X> X existingInstance(Class<X> c, InstancesState scopedMap) {
		return (X) scopedMap.get(c);
	}

	private <X> X createPostProcessedInstance(Class<X> c, InstancesState scopedMap, Consumer<Object> postProcessor, ThreadLocal<Boolean> directConstructionPhase) {
		X instance = createInstance(c, scopedMap, directConstructionPhase);
		rejectCachingAfterShutdown(c, instance);
		cacheAndPostProcess(c, scopedMap, postProcessor, instance);
		return instance;
	}

	private void rejectCachingAfterShutdown(Class<?> c, Object instance) {
		if (shutdownState.get() == ContainerStatus.ACTIVE) {
			// Running normally: the instance is cached below.
		} else {
			// Lost the race against shutdown: this chain passed the entry guard
			// before shutdown began but finished after. Never cache it (the
			// shutdown drain may already have taken its final snapshot) — destroy
			// it immediately instead, best-effort, then fail fast.
			destroyImmediatelyBestEffort(c, instance);
			throw new ContainerShutdown("Container is shutting down or has been shut down; newly created instance of '%s' was destroyed immediately instead of caching.".formatted(c.getName()));
		}
	}

	private void destroyImmediatelyBestEffort(Class<?> c, Object instance) {
		try {
			immediateDestroyer.accept(instance);
		} catch (Throwable t) {
			ContainerShutdown shutdown = new ContainerShutdown("Container is shutting down or has been shut down; newly created instance of '%s' was destroyed immediately instead of caching.".formatted(c.getName()));
			shutdown.addSuppressed(t);
			throw shutdown;
		}
	}

	private <X> void cacheAndPostProcess(Class<X> c, InstancesState scopedMap, Consumer<Object> postProcessor, X instance) {
		scopedMap.put(c, instance);
		log.debug("Created instance of {}", c.getName());
		try {
			postProcessor.accept(instance);
		} catch (RuntimeException | Error e) {
			// The bean is fully constructed but post-processing (field injection,
			// @PostConstruct) failed: remove it again so no half-initialized
			// instance stays behind in the cache. Deliberately including Error:
			// even a failed Error must not leave a broken entry behind
			// (precise rethrow keeps the original unchecked type, no throws
			// declaration needed). Field-injection cycles keep working because
			// the early put above is unchanged for the success path; only the
			// failure path compensates.
			scopedMap.remove(c);
			throw e;
		}
	}

	private <X> X createInstance(Class<?> c, InstancesState scopedMap, ThreadLocal<Boolean> directConstructionPhase) {
		synchronized (scopedMap.getLockFor(c)) {
			rejectCircularConstruction(c, scopedMap);
			return constructWithPhaseTracking(c, scopedMap, directConstructionPhase);
		}
	}

	private void rejectCircularConstruction(Class<?> c, InstancesState scopedMap) {
		if (scopedMap.isUnderConstruction(c)) {
			throw new UnderConstruction("'%s' is still under construction.".formatted(c.getName()));
		}
	}

	private <X> X constructWithPhaseTracking(Class<?> c, InstancesState scopedMap, ThreadLocal<Boolean> directConstructionPhase) {
		scopedMap.markUnderConstruction(c);
		Boolean previousPhase = directConstructionPhase.get();
		directConstructionPhase.set(true);
		try {
			return instantiateWith(getConstructor(c));
		} catch (UnderConstruction cd) {
			throw new ConstructorCycle("Cycle in dependencies detected while performing constructor injection on '%s'.".formatted(c.getName()), cd);
		} catch (Exception e) {
			throw new BeanInstantiationFailed("Failed to instantiate class '%s'.".formatted(c.getName()), e);
		} finally {
			restoreConstructionPhase(directConstructionPhase, previousPhase);
			scopedMap.unmarkUnderConstruction(c);
		}
	}

	private void restoreConstructionPhase(ThreadLocal<Boolean> directConstructionPhase, Boolean previousPhase) {
		// Save/restore (not set/reset): resolutions nest - an outer chain resolving its 2nd+
		// constructor parameter after a nested chain finished must still observe "in direct
		// construction", otherwise nested dependencies would resolve as proxies instead of
		// real objects. A null/outermost previous value removes the entry (no pool leak).
		if (previousPhase == null) {
			directConstructionPhase.remove();
		} else {
			directConstructionPhase.set(previousPhase);
		}
	}

	@SuppressWarnings("unchecked")
	private <X> X instantiateWith(Constructor<?> constructor) throws InstantiationException, IllegalAccessException, InvocationTargetException {
		return (X) constructor.newInstance(resolveConstructorParameters(constructor.getParameterTypes()));
	}

	private Object[] resolveConstructorParameters(Class<?>[] parameterTypes) {
		List<Object> parameters = new ArrayList<>();
		for (var parameterType : parameterTypes) {
			parameters.add(resolveConstructorParameter(parameterType));
		}
		return parameters.toArray(new Object[0]);
	}

	private Object resolveConstructorParameter(Class<?> parameterType) {
		if (oopdi != null && OOPDI.class.isAssignableFrom(parameterType)) {
			return this.oopdi;
		} else {
			return context.getOrCreate(parameterType);
		}
	}

	private Constructor<?> getConstructor(Class<?> c) {
		ClassMetadata metadata = metadataRepository.getMetadata(c);
		Constructor<?> primaryConstructor = metadata.getPrimaryConstructor();
		if (primaryConstructor == null) {
			throw new NoAccessibleConstructor("No accessible constructor found for '%s'.".formatted(c.getName()));
		}
		return primaryConstructor;
	}
	
	private <A> void checkNonAbstract(Class<A> c) {
		if (Modifier.isAbstract(c.getModifiers())) {
			throw new AbstractBean("Cannot instantiate class '%s': it is abstract.".formatted(c.getName()));
		}
	}

	private <A> void checkInjectableAnnotated(Class<A> c) {
		if (!c.isAnnotationPresent(Injectable.class)) {
			throw new NotInjectableBean("Will not instantiate class '%s': it is not annotated as 'Injectable'.".formatted(c.getName()));
		}
	}
}