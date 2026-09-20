package de.oopexpert.oopdi.resolver;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;
import java.util.Optional;

public sealed interface InjectionPoint permits FieldInjectionPoint, ParameterInjectionPoint {

    Class<?> getType();

    Type getGenericType();

    AnnotatedElement getAnnotatedElement();

    Class<?> getDeclaringClass();

    <A extends Annotation> Optional<A> findAnnotation(Class<A> annotationClass);

    boolean hasAnnotation(Class<? extends Annotation> annotationClass);
    
}