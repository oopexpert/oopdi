package de.oopexpert.oopdi.exception;

/**
 * Thrown when a bean is requested (directly or as a nested dependency) after the container has
 * started shutting down. Analogous to {@link WarmupFailed} for a failed background metadata
 * warmup: creation after this point would produce instances that can never be destroyed again,
 * so the request fails fast instead of leaking silently.
 */
public class ContainerShutdown extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ContainerShutdown(String message) {
		super(message);
	}

}
