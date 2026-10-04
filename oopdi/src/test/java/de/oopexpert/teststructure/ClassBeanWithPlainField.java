package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.Injectable;

/**
 * An {@code @Injectable} bean with a {@code final}, UNANNOTATED field of a project-local,
 * non-{@code @Injectable} type, set only in the constructor. Reproduces the field-injection
 * bug: before the fix, the constructor-parameter type-name fallback in
 * {@code InstanceDependencyResolver} also matched unannotated fields, so the framework tried
 * to resolve {@link ClassPlainHelper} as a managed bean and failed with
 * {@code NotInjectableBean} ("it is not annotated as 'Injectable'") instead of leaving the
 * constructor-assigned value untouched.
 */
@Injectable
public class ClassBeanWithPlainField {

	private final ClassPlainHelper helper;

	public ClassBeanWithPlainField() {
		this.helper = new ClassPlainHelper("constructor-assigned");
	}

	public ClassPlainHelper getHelper() {
		return helper;
	}

}
