package de.oopexpert.oopdi.resolver.impl;

import de.oopexpert.oopdi.annotation.InjectInstance;
import de.oopexpert.oopdi.resolver.DependencyResolutionContext;
import de.oopexpert.oopdi.resolver.DependencyResolver;
import de.oopexpert.oopdi.resolver.InjectionPoint;

public final class InstanceDependencyResolver implements DependencyResolver {

	@Override
	public boolean supports(InjectionPoint point) {
		return point.hasAnnotation(InjectInstance.class)
				|| (!point.getType().isPrimitive() && !point.getType().getName().startsWith("java."));
	}

	@Override
	public Object resolve(InjectionPoint point, DependencyResolutionContext context) {
		Class<?> targetType = point.getType();

		if (context.isDirectConstructionPhase()) {
			return context.getOrCreate(targetType);
		}
		return context.getOrCreateProxy(targetType);
	}
}