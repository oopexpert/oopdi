package de.oopexpert.oopdi;

public class UnderConstruction extends RuntimeException {

	public UnderConstruction(String string) {
		super(string);
	}

	public UnderConstruction(String string, UnderConstruction cd) {
		super(string, cd);
	}

	private static final long serialVersionUID = 2028845835382196862L;

}
