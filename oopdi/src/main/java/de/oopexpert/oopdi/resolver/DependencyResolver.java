package de.oopexpert.oopdi.resolver;

public interface DependencyResolver {

    /**
     * Prüft, ob dieser Resolver den gegebenen Injektionspunkt verarbeiten kann.
     */
    boolean supports(InjectionPoint point);

    /**
     * Löst den Zielwert für den gegebenen Injektionspunkt auf.
     */
    Object resolve(InjectionPoint point, DependencyResolutionContext context);
}