package de.oopexpert.oopdi.exception;

public class CannotInject extends RuntimeException {

	public CannotInject(String string, RuntimeException e) {
		super(string, e);
	}

	public CannotInject(String string) {
		super(string);
	}

	private static final long serialVersionUID = -9211905851588547171L;

}
