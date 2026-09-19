package de.oopexpert.oopdi.resolver;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Optional;

public record FieldInjectionPoint(Field field) implements InjectionPoint {
    @Override public Class<?> getType() { return field.getType(); }
    @Override public Type getGenericType() { return field.getGenericType(); }
    @Override public AnnotatedElement getAnnotatedElement() { return field; }
    @Override public Class<?> getDeclaringClass() { return field.getDeclaringClass(); }

    @Override
    public <A extends Annotation> Optional<A> findAnnotation(Class<A> annotationClass) {
        return Optional.ofNullable(field.getAnnotation(annotationClass));
    }

    @Override
    public boolean hasAnnotation(Class<? extends Annotation> annotationClass) {
        return field.isAnnotationPresent(annotationClass);
    }
}