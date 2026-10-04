package de.oopexpert.oopdi.exception;

public class NotInjectableBean extends RuntimeException {

	public NotInjectableBean(String message) {
		super(message);
	}

	private static final long serialVersionUID = 1L;

}
