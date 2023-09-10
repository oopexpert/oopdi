package de.oopexpert.oopdi;

import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Injectable {

	Scope scope() default Scope.GLOBAL;

	String[] profiles() default {};

}
