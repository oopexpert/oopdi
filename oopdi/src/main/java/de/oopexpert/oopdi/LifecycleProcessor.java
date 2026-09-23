package de.oopexpert.oopdi;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.oopexpert.oopdi.exception.MultiplePostConstructMethods;
import de.oopexpert.oopdi.exception.MultiplePreDestroyMethods;
import de.oopexpert.oopdi.metadata.ClassMetadata;
import de.oopexpert.oopdi.metadata.MetadataRepository;
import de.oopexpert.oopdi.resolver.DependencyResolutionContext;

public class LifecycleProcessor {

	private final DependencyResolutionContext context;
	private final OOPDI<?> oopdi;
	private final MetadataRepository metadataRepository;

	public LifecycleProcessor(DependencyResolutionContext context, OOPDI<?> oopdi, MetadataRepository metadataRepository) {
		this.context = Objects.requireNonNull(context, "context must not be null");
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository must not be null");
		this.oopdi = oopdi;
	}

	public void executePostConstruct(Object instance) {
		Optional<java.lang.reflect.Method> postConstruct = findPostConstructMethod(
				metadataRepository.getMetadata(instance.getClass()));

		if (postConstruct.isPresent()) {
			var method = postConstruct.get();
			var parameterTypes = method.getParameterTypes();
			method.setAccessible(true);
			try {
				method.invoke(instance, resolveParameters(parameterTypes));
			} catch (IllegalAccessException | InvocationTargetException e) {
				throw new RuntimeException("Failed to invoke @PostConstruct method '%s' on '%s'.".formatted(method.getName(), instance.getClass().getName()), e);
			}
		}
	}

	public void invokePreDestroy(Object instance) {
		Optional<java.lang.reflect.Method> preDestroy = findPreDestroyMethod(
				metadataRepository.getMetadata(instance.getClass()));

		if (preDestroy.isPresent()) {
			var method = preDestroy.get();
			method.setAccessible(true);
			try {
				method.invoke(instance);
			} catch (IllegalAccessException | InvocationTargetException e) {
				throw new RuntimeException("Failed to invoke @PreDestroy method '%s' on '%s'.".formatted(method.getName(), instance.getClass().getName()), e);
			}
		}
	}

	/**
	 * Finds the single {@code @PostConstruct} method of a class hierarchy, shared by runtime
	 * invocation and startup graph validation so both enforce the same cardinality with the
	 * same message.
	 */
	public Optional<java.lang.reflect.Method> findPostConstructMethod(ClassMetadata metadata) {
		Set<java.lang.reflect.Method> postConstructMethods = metadata.getPostConstructMethods();

		if (postConstructMethods.size() > 1) {
			throw new MultiplePostConstructMethods(metadata.getTargetClass());
		}

		return postConstructMethods.stream().findFirst();
	}

	/**
	 * Finds the single {@code @PreDestroy} method of a class hierarchy, shared by runtime
	 * invocation and startup graph validation so both enforce the same cardinality with the
	 * same message.
	 */
	public Optional<java.lang.reflect.Method> findPreDestroyMethod(ClassMetadata metadata) {
		Set<java.lang.reflect.Method> preDestroyMethods = metadata.getPreDestroyMethods();

		if (preDestroyMethods.size() > 1) {
			throw new MultiplePreDestroyMethods(metadata.getTargetClass());
		}

		return preDestroyMethods.stream().findFirst();
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