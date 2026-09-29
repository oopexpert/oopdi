package de.oopexpert.oopdi.exception;

/**
 * Thrown when beans are requested (or the bean graph is validated) before the container has
 * been started via {@code OOPDI.startup()}. Sibling of {@link ContainerShutdown} for the other
 * end of the lifecycle: implicit auto-start would hide a missing startup call, so the wrong
 * access fails fast instead.
 */
public class ContainerNotStarted extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ContainerNotStarted(String message) {
		super(message);
	}

}
