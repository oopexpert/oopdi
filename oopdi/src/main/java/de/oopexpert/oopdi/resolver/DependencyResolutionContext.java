package de.oopexpert.oopdi.resolver;

public interface DependencyResolutionContext {

    <A> A getOrCreate(Class<A> clazz);

    boolean isDirectConstructionPhase();
    
}