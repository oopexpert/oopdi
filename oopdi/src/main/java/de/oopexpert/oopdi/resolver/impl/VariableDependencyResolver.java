package de.oopexpert.oopdi.resolver.impl;

import java.util.Objects;

import de.oopexpert.oopdi.VariableSource;
import de.oopexpert.oopdi.annotation.InjectVariable;
import de.oopexpert.oopdi.exception.CannotInject;
import de.oopexpert.oopdi.parser.TypeParserRegistry;
import de.oopexpert.oopdi.resolver.DependencyResolutionContext;
import de.oopexpert.oopdi.resolver.DependencyResolver;
import de.oopexpert.oopdi.resolver.InjectionPoint;

public final class VariableDependencyResolver implements DependencyResolver {

	private final TypeParserRegistry typeParserRegistry;

	public VariableDependencyResolver() {
		this(new TypeParserRegistry());
	}

	public VariableDependencyResolver(TypeParserRegistry typeParserRegistry) {
		this.typeParserRegistry = Objects.requireNonNull(typeParserRegistry, "typeParserRegistry must not be null");
	}

	@Override
	public boolean supports(InjectionPoint point) {
		return point.hasAnnotation(InjectVariable.class);
	}

	@Override
	public Object resolve(InjectionPoint point, DependencyResolutionContext context) {
		InjectVariable annotation = point.findAnnotation(InjectVariable.class).orElseThrow();
		VariableSource source = annotation.source();
		String key = annotation.key();
		String valueByKey = source.getValueByKey(key);

		if (valueByKey == null) {
			if (!annotation.defaultValue().isEmpty()) {
				valueByKey = annotation.defaultValue();
			} else if (annotation.optional()) {
				if (point.getType().isPrimitive()) {
					throw new CannotInject("Cannot inject variable: key '%s' not found in source %s for field in '%s' and field type '%s' is primitive, which cannot hold null. Use defaultValue or a boxed type instead.".formatted(key, source.name(), point.getDeclaringClass().getName(), point.getType().getName()));
				}
				return null;
			} else {
				throw new CannotInject("Cannot inject variable: key '%s' not found in source %s for field in '%s'.".formatted(key, source.name(), point.getDeclaringClass().getName()));
			}
		}

		try {
			return typeParserRegistry.parse(valueByKey, point.getType());
		} catch (IllegalArgumentException e) {
			throw new CannotInject("Cannot inject variable: invalid format for key '%s' in source %s for field in '%s'.".formatted(key, source.name(), point.getDeclaringClass().getName()), e);
		}
	}
}