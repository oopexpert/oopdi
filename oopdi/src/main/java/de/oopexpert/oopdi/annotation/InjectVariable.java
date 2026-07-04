package de.oopexpert.oopdi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import de.oopexpert.oopdi.VariableSource;

@Retention(RetentionPolicy.RUNTIME)  // Retained at runtime for reflection
@Target(ElementType.FIELD)          // Applicable to fields
public @interface InjectVariable {

    // The source of the variable to inject (e.g., system, parameter, file).
    VariableSource source();

    // The key of the environment variable or property to inject.
    String key();

    // If true, a missing key does not throw — injects null (or zero for primitives).
    // Overridden by defaultValue if defaultValue is non-empty.
    boolean optional() default false;

    // Fallback value injected when the key is not found in the source.
    // An empty string means "no default set". Takes precedence over optional().
    String defaultValue() default "";

}