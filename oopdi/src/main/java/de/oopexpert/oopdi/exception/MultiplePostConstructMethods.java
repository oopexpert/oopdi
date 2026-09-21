package de.oopexpert.oopdi.exception;

public class MultiplePostConstructMethods extends RuntimeException {

	public MultiplePostConstructMethods(Class<? extends Object> class1) {
		super("Multiple PostConstruct methods found in class '%s'. Cannot decide which to invoke!".formatted(class1.getName()));
	}

	private static final long serialVersionUID = 7712380723841048508L;
	
}
