package de.oopexpert.oopdi.metadata;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import de.oopexpert.oopdi.resolver.FieldInjectionPoint;
import de.oopexpert.oopdi.resolver.InjectionPoint;

public final class ClassMetadata {

	private final Class<?> targetClass;
	private final Constructor<?> primaryConstructor;
	private final List<InjectionPoint> fieldInjectionPoints;
	private final Set<Method> postConstructMethods;
	private final Set<Method> preDestroyMethods;

	public ClassMetadata(Class<?> targetClass,
			Constructor<?> primaryConstructor,
			List<Field> fields,
			Set<Method> postConstructMethods,
			Set<Method> preDestroyMethods) {
		this.targetClass = Objects.requireNonNull(targetClass);
		this.primaryConstructor = primaryConstructor;
		this.fieldInjectionPoints = fields.stream()
				.map(FieldInjectionPoint::new)
				.map(InjectionPoint.class::cast)
				.toList();
		this.postConstructMethods = Set.copyOf(postConstructMethods);
		this.preDestroyMethods = Set.copyOf(preDestroyMethods);
	}

	public Class<?> getTargetClass() {
		return targetClass;
	}

	public Constructor<?> getPrimaryConstructor() {
		return primaryConstructor;
	}

	public List<InjectionPoint> getFieldInjectionPoints() {
		return fieldInjectionPoints;
	}

	public Set<Method> getPostConstructMethods() {
		return postConstructMethods;
	}

	public Set<Method> getPreDestroyMethods() {
		return preDestroyMethods;
	}
}