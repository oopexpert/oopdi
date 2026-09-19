package de.oopexpert.oopdi;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.exception.CannotInject;
import de.oopexpert.oopdi.exception.MultipleConstructors;
import de.oopexpert.oopdi.exception.UnderConstruction;
import de.oopexpert.oopdi.resolver.DependencyResolutionContext;

public class InstanceFactory {

	private static final Logger log = LoggerFactory.getLogger(InstanceFactory.class);

	private final DependencyResolutionContext context;
	private final ClassesResolver classesResolver;
	private final OOPDI<?> oopdi;

	public InstanceFactory(DependencyResolutionContext context, ClassesResolver classesResolver, OOPDI<?> oopdi) {
		this.context = Objects.requireNonNull(context, "context must not be null");
		this.classesResolver = Objects.requireNonNull(classesResolver, "classesResolver must not be null");
		this.oopdi = oopdi;
	}

	public <A> void validateEligible(Class<A> c) {
		checkInjectableAnnotated(c);
		checkNonAbstract(c);
	}

	public <A> void checkImmediateInstantiationConfiguration(Class<A> c) {
		if (ProxyManager.isImmediateInstantiationRequested(c) && !Scope.isImmediateInstantiationPossible(c)) {
			throw new RuntimeException("Misconfiguration of class " + c.getName()
					+ ": it is configured to be instantiated immediately, but this is only possible with scopes GLOBAL and THREAD.");
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
					scopedMap.put(c, instance);
					log.debug("Created instance of {}", c.getName());
					postProcessor.accept(instance);
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
			throw new RuntimeException(e);
		}
	}

	private <X> X createInstance(Class<?> c, InstancesState scopedMap, ThreadLocal<Boolean> directConstructionPhase) {
		synchronized (scopedMap.getLockFor(c)) {
			if (scopedMap.isUnderConstruction(c)) {
				throw new UnderConstruction(c.getName() + " is still under construction.");
			}
			scopedMap.markUnderConstruction(c);
			directConstructionPhase.set(true);
			try {
				return instanciateWith(getConstructor(c));
			} catch (UnderConstruction cd) {
				throw new CannotInject("Cycle in dependencies detected while performing constructor injection on " + c.getName(), cd);
			} catch (Exception e) {
				throw new CannotInject("Failed to instantiate class " + c.getName(), e);
			} finally {
				directConstructionPhase.set(false);
				scopedMap.unmarkUnderConstruction(c);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private <X> X instanciateWith(Constructor<?> constructor) throws InstantiationException, IllegalAccessException, InvocationTargetException {
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
		var declaredConstructors = c.getDeclaredConstructors();
		if (declaredConstructors.length > 1) {
			throw new MultipleConstructors("Multiple constructors for class '" + c.getName() + "'. Cannot decide.");
		}
		return declaredConstructors[0];
	}

	private <A> void checkNonAbstract(Class<A> c) {
		if (Modifier.isAbstract(c.getModifiers())) {
			throw new RuntimeException("Cannot instantiate Class " + c.getName() + ". It is abstract!");
		}
	}

	private <A> void checkInjectableAnnotated(Class<A> c) {
		if (!c.isAnnotationPresent(Injectable.class)) {
			throw new RuntimeException("Will not instantiate Class " + c.getName() + ". It is not annotated as 'Injectable'!");
		}
	}
}