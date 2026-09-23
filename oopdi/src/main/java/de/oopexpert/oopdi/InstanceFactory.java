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
import de.oopexpert.oopdi.exception.CannotInject;
import de.oopexpert.oopdi.exception.ContainerShutdown;
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
	private final Supplier<ShutdownStatus> shutdownState;
	private final Consumer<Object> immediateDestroyer;

	public InstanceFactory(DependencyResolutionContext context, ClassesResolver classesResolver, OOPDI<?> oopdi, MetadataRepository metadataRepository,
			Supplier<ShutdownStatus> shutdownState, Consumer<Object> immediateDestroyer) {
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
			throw new CannotInject("Misconfiguration of class '%s': it is configured to be instantiated immediately, but this is only possible with scope GLOBAL.".formatted(c.getName()));
		}
	}

	public <X> X getOrCreateInjectable(Class<X> x, ScopedInstances scopedInstances, Consumer<Object> postProcessor, ThreadLocal<Boolean> directConstructionPhase) {
		try {
			@SuppressWarnings("unchecked")
			Class<X> c = (Class<X>) classesResolver.determineRelevantClass(x);
			InstancesState scopedMap = scopedInstances.getScopedInstancesState(Scope.of(c));
			X instance;
			synchronized (scopedMap.getLockFor(c)) {
				if (!scopedMap.instanceExists(c)) {
					instance = createInstance(c, scopedMap, directConstructionPhase);
					if (shutdownState.get() != ShutdownStatus.ACTIVE) {
						// Lost the race against shutdown: this chain passed the entry guard
						// before shutdown began but finished after. Never cache it (the
						// shutdown drain may already have taken its final snapshot) — destroy
						// it immediately instead, best-effort, then fail fast.
						try {
							immediateDestroyer.accept(instance);
						} catch (Throwable t) {
							ContainerShutdown shutdown = new ContainerShutdown("Container is shutting down or has been shut down; newly created instance of '%s' was destroyed immediately instead of caching.".formatted(c.getName()));
							shutdown.addSuppressed(t);
							throw shutdown;
						}
						throw new ContainerShutdown("Container is shutting down or has been shut down; newly created instance of '%s' was destroyed immediately instead of caching.".formatted(c.getName()));
					}
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
				} else {
					@SuppressWarnings("unchecked")
					X existing = (X) scopedMap.get(c);
					instance = existing;
				}
			}
			return instance;
		} catch (RuntimeException re) {
			throw re;
		} catch (Exception e) {
			throw new CannotInject("Failed to resolve bean for '%s'.".formatted(x.getName()), e);
		}
	}

	private <X> X createInstance(Class<?> c, InstancesState scopedMap, ThreadLocal<Boolean> directConstructionPhase) {
		synchronized (scopedMap.getLockFor(c)) {
			if (scopedMap.isUnderConstruction(c)) {
				throw new UnderConstruction("'%s' is still under construction.".formatted(c.getName()));
			}
			scopedMap.markUnderConstruction(c);
			// Save/restore (not set/reset): resolutions nest - an outer chain resolving its 2nd+
			// constructor parameter after a nested chain finished must still observe "in direct
			// construction", otherwise nested dependencies would resolve as proxies instead of
			// real objects. A null/outermost previous value removes the entry (no pool leak).
			Boolean previousPhase = directConstructionPhase.get();
			directConstructionPhase.set(true);
		try {
			return instantiateWith(getConstructor(c));
		} catch (UnderConstruction cd) {
			throw new CannotInject("Cycle in dependencies detected while performing constructor injection on '%s'.".formatted(c.getName()), cd);
		} catch (Exception e) {
			throw new CannotInject("Failed to instantiate class '%s'.".formatted(c.getName()), e);
			} finally {
				if (previousPhase == null) {
					directConstructionPhase.remove();
				} else {
					directConstructionPhase.set(previousPhase);
				}
				scopedMap.unmarkUnderConstruction(c);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private <X> X instantiateWith(Constructor<?> constructor) throws InstantiationException, IllegalAccessException, InvocationTargetException {
		return (X) constructor.newInstance(resolveConstructorParameters(constructor.getParameterTypes()));
	}

	private Object[] resolveConstructorParameters(Class<?>[] parameterTypes) {
		List<Object> parameters = new ArrayList<>();
		for (var parameterType : parameterTypes) {
			if (oopdi != null && OOPDI.class.isAssignableFrom(parameterType)) {
				parameters.add(this.oopdi);
			} else {
				parameters.add(context.getOrCreate(parameterType));
			}
		}
		return parameters.toArray(new Object[0]);
	}

	private Constructor<?> getConstructor(Class<?> c) {
		ClassMetadata metadata = metadataRepository.getMetadata(c);
		Constructor<?> primaryConstructor = metadata.getPrimaryConstructor();
		if (primaryConstructor == null) {
			throw new CannotInject("No accessible constructor found for '%s'.".formatted(c.getName()));
		}
		return primaryConstructor;
	}
	
	private <A> void checkNonAbstract(Class<A> c) {
		if (Modifier.isAbstract(c.getModifiers())) {
			throw new CannotInject("Cannot instantiate class '%s': it is abstract.".formatted(c.getName()));
		}
	}

	private <A> void checkInjectableAnnotated(Class<A> c) {
		if (!c.isAnnotationPresent(Injectable.class)) {
			throw new CannotInject("Will not instantiate class '%s': it is not annotated as 'Injectable'.".formatted(c.getName()));
		}
	}
}