package de.oopexpert.oopdi.resolver;

public interface DependencyResolutionContext {

    <A> A getOrCreate(Class<A> clazz);

    <A> A getOrCreateProxy(Class<A> clazz);

    boolean isDirectConstructionPhase();
    
}