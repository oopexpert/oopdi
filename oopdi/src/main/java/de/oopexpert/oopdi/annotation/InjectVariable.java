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

}