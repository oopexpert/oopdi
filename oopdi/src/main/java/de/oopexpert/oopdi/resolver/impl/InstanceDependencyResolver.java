package de.oopexpert.oopdi.resolver.impl;

import de.oopexpert.oopdi.annotation.InjectInstance;
import de.oopexpert.oopdi.resolver.DependencyResolutionContext;
import de.oopexpert.oopdi.resolver.DependencyResolver;
import de.oopexpert.oopdi.resolver.InjectionPoint;
import de.oopexpert.oopdi.resolver.InternalResolutionContext;

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
		// Proxy issuance is a framework-internal capability: Context is the single
		// implementation of InternalResolutionContext, so this cast always succeeds.
		return ((InternalResolutionContext) context).getOrCreateProxy(targetType);
	}
}