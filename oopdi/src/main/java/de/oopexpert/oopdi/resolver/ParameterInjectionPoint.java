package de.oopexpert.oopdi.resolver;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Optional;

public record ParameterInjectionPoint(Parameter parameter, Class<?> declaringClass) implements InjectionPoint {
    @Override public Class<?> getType() { return parameter.getType(); }
    @Override public Type getGenericType() { return parameter.getParameterizedType(); }
    @Override public AnnotatedElement getAnnotatedElement() { return parameter; }
    @Override public Class<?> getDeclaringClass() { return declaringClass; }

    @Override
    public <A extends Annotation> Optional<A> findAnnotation(Class<A> annotationClass) {
        return Optional.ofNullable(parameter.getAnnotation(annotationClass));
    }

    @Override
    public boolean hasAnnotation(Class<? extends Annotation> annotationClass) {
        return parameter.isAnnotationPresent(annotationClass);
    }
}
