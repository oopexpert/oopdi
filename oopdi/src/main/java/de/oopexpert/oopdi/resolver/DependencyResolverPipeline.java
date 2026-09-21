package de.oopexpert.oopdi.resolver;

import java.util.List;

import de.oopexpert.oopdi.exception.CannotInject;

public final class DependencyResolverPipeline {

    private final List<DependencyResolver> resolvers;

    public DependencyResolverPipeline(List<DependencyResolver> resolvers) {
        this.resolvers = List.copyOf(resolvers);
    }

    public boolean supports(InjectionPoint point) {
        return resolvers.stream().anyMatch(r -> r.supports(point));
    }

    public Object resolve(InjectionPoint point, DependencyResolutionContext context) {
        for (DependencyResolver resolver : resolvers) {
            if (resolver.supports(point)) {
                return resolver.resolve(point, context);
            }
        }
        throw new CannotInject("No matching DependencyResolver found for injection point '%s' declared in '%s'.".formatted(point.getType().getName(), point.getDeclaringClass().getName()));
    }
}