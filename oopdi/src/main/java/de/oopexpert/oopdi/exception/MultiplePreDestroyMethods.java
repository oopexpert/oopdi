package de.oopexpert.oopdi.exception;

/**
 * Thrown when more than one {@code @PreDestroy} method is found in a class hierarchy. Mirrors
 * {@link MultiplePostConstructMethods}: exactly one lifecycle method per hierarchy is allowed.
 */
public class MultiplePreDestroyMethods extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public MultiplePreDestroyMethods(Class<? extends Object> clazz) {
		super("Multiple @PreDestroy methods found in class hierarchy of '%s'. Only one is allowed.".formatted(clazz.getName()));
	}

}
