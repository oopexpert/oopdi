package de.oopexpert.oopdi.exception;

public class ConstructorCycle extends RuntimeException {

	public ConstructorCycle(String message, Throwable cause) {
		super(message, cause);
	}

	private static final long serialVersionUID = 1L;

}
