package de.oopexpert.oopdi.resolver.impl;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import de.oopexpert.oopdi.ClassesResolver;
import de.oopexpert.oopdi.annotation.InjectSet;
import de.oopexpert.oopdi.resolver.DependencyResolutionContext;
import de.oopexpert.oopdi.resolver.DependencyResolver;
import de.oopexpert.oopdi.resolver.InjectionPoint;

public final class SetDependencyResolver implements DependencyResolver {

	private final ClassesResolver classesResolver;

	public SetDependencyResolver(ClassesResolver classesResolver) {
		this.classesResolver = Objects.requireNonNull(classesResolver, "classesResolver must not be null");
	}

	@Override
	public boolean supports(InjectionPoint point) {
		return point.hasAnnotation(InjectSet.class);
	}

	@Override
	public Object resolve(InjectionPoint point, DependencyResolutionContext context) {
		InjectSet annotation = point.findAnnotation(InjectSet.class).orElseThrow();
		Class<?> hintClass = annotation.hint();
		Set<Class<?>> targetClasses = classesResolver.getSet(hintClass);

		return targetClasses.stream()
				.map(context::getOrCreateProxy)
				.collect(Collectors.toUnmodifiableSet());
	}
}