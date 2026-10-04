package de.oopexpert.oopdi.resolver.impl;

import de.oopexpert.oopdi.annotation.InjectInstance;
import de.oopexpert.oopdi.resolver.DependencyResolutionContext;
import de.oopexpert.oopdi.resolver.DependencyResolver;
import de.oopexpert.oopdi.resolver.FieldInjectionPoint;
import de.oopexpert.oopdi.resolver.InjectionPoint;
import de.oopexpert.oopdi.resolver.InternalResolutionContext;

public final class InstanceDependencyResolver implements DependencyResolver {

	/**
	 * The type-name fallback ("anything not java.* and not primitive is an injectable bean")
	 * is documented and intentional for constructor parameters (README: every parameter type
	 * is resolved as a managed bean), but must never apply to fields: per README, fields are
	 * only injected when explicitly annotated with {@code @InjectInstance}, {@code @InjectSet}
	 * or {@code @InjectVariable}. Without this distinction, unannotated fields holding plain
	 * (non-{@code java.*}) helper objects set in the constructor - e.g. {@code javax.swing.Timer}
	 * (outside the "java." prefix) or a project-local non-{@code @Injectable} helper class -
	 * were misdetected as injection points and either overwritten or rejected with
	 * {@code NotInjectableBean}.
	 */
	@Override
	public boolean supports(InjectionPoint point) {
		if (point instanceof FieldInjectionPoint) {
			return point.hasAnnotation(InjectInstance.class);
		}
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