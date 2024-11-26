package de.oopexpert.oopdi.exception;

public class MultiplePostConstructMethods extends RuntimeException {

	public MultiplePostConstructMethods(Class<? extends Object> class1) {
		super("Multiple PostConstruct methods found in class '" + class1.getName() +"' Cannot decide which to invoke!");
	}

	private static final long serialVersionUID = 7712380723841048508L;
	
}
