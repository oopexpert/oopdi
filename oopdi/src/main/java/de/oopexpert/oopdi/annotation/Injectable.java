package de.oopexpert.oopdi.annotation;

import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import de.oopexpert.oopdi.Scope;

@Target(TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Injectable {

	Scope scope() default Scope.GLOBAL;

	String[] profiles() default {};

}
