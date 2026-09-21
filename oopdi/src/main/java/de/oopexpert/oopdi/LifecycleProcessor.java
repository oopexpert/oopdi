package de.oopexpert.oopdi;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
		ClassMetadata metadata = metadataRepository.getMetadata(instance.getClass());
		Set<java.lang.reflect.Method> postConstructMethods = metadata.getPostConstructMethods();

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
				throw new RuntimeException("Failed to invoke @PostConstruct method '%s' on '%s'.".formatted(method.getName(), instance.getClass().getName()), e);
			}
		}
	}

	public void invokePreDestroy(Object instance) {
		ClassMetadata metadata = metadataRepository.getMetadata(instance.getClass());
		Set<java.lang.reflect.Method> preDestroyMethods = metadata.getPreDestroyMethods();

		if (preDestroyMethods.size() > 1) {
			throw new MultiplePreDestroyMethods(instance.getClass());
		}
		if (!preDestroyMethods.isEmpty()) {
			var method = preDestroyMethods.iterator().next();
			method.setAccessible(true);
			try {
				method.invoke(instance);
			} catch (IllegalAccessException | InvocationTargetException e) {
				throw new RuntimeException("Failed to invoke @PreDestroy method '%s' on '%s'.".formatted(method.getName(), instance.getClass().getName()), e);
			}
		}
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