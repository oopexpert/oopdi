package de.oopexpert.teststructure;

/**
 * Deliberately NOT {@code @Injectable}: a plain, project-local helper type with no relation
 * to OOPDI. Used to reproduce the field-injection bug where an unannotated field whose type
 * has no "java." prefix was misdetected as an injection point by the constructor-parameter
 * type-name fallback in {@code InstanceDependencyResolver}.
 */
public class ClassPlainHelper {

	private final String label;

	public ClassPlainHelper(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

}
