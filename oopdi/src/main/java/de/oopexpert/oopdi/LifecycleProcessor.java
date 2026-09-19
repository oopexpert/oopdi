package de.oopexpert.oopdi;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import de.oopexpert.oopdi.annotation.PostConstruct;
import de.oopexpert.oopdi.annotation.PreDestroy;
import de.oopexpert.oopdi.exception.MultiplePostConstructMethods;
import de.oopexpert.oopdi.resolver.DependencyResolutionContext;

public class LifecycleProcessor {

	private final DependencyResolutionContext context;
	private final OOPDI<?> oopdi;

	public LifecycleProcessor(DependencyResolutionContext context, OOPDI<?> oopdi) {
		this.context = Objects.requireNonNull(context, "context must not be null");
		this.oopdi = oopdi;
	}

	public void executePostConstruct(Object instance) {
		var postConstructMethods = collectPostConstructMethods(instance.getClass());

		if (postConstructMethods.size() > 1) {
			throw new MultiplePostConstructMethods(instance.getClass());
		}

		if (!postConstructMethods.isEmpty()) {
			var method = postConstructMethods.iterator().next();
			var parameterTypes = method.getParameterTypes();
			method.setAccessible(true);
			try {
				method.invoke(instance, resolveParameters(parameterTypes));
			} catch (IllegalAccessException | InvocationTargetException e) {
				throw new RuntimeException("Failed to invoke @PostConstruct method '" + method.getName() + "' on " + instance.getClass().getName(), e);
			}
		}
	}

	public void invokePreDestroy(Object instance) {
		var preDestroyMethods = collectPreDestroyMethods(instance.getClass());
		if (preDestroyMethods.size() > 1) {
			throw new RuntimeException("Multiple @PreDestroy methods found in class hierarchy of "
					+ instance.getClass().getName() + ". Only one is allowed.");
		}
		if (!preDestroyMethods.isEmpty()) {
			var method = preDestroyMethods.iterator().next();
			method.setAccessible(true);
			try {
				method.invoke(instance);
			} catch (IllegalAccessException | InvocationTargetException e) {
				throw new RuntimeException("Failed to invoke @PreDestroy method '" + method.getName()
						+ "' on " + instance.getClass().getName(), e);
			}
		}
	}

	private Set<Method> collectPostConstructMethods(Class<?> clazz) {
		var methods = new HashSet<Method>();
		if (clazz == null) {
			return methods;
		}
		Arrays.stream(clazz.getDeclaredMethods())
				.filter(m -> m.isAnnotationPresent(PostConstruct.class))
				.forEach(methods::add);
		methods.addAll(collectPostConstructMethods(clazz.getSuperclass()));
		return methods;
	}

	private Set<Method> collectPreDestroyMethods(Class<?> clazz) {
		var methods = new HashSet<Method>();
		if (clazz == null) {
			return methods;
		}
		Arrays.stream(clazz.getDeclaredMethods())
				.filter(m -> m.isAnnotationPresent(PreDestroy.class))
				.forEach(methods::add);
		methods.addAll(collectPreDestroyMethods(clazz.getSuperclass()));
		return methods;
	}

	private Object[] resolveParameters(Class<?>[] parameterTypes) {
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
}