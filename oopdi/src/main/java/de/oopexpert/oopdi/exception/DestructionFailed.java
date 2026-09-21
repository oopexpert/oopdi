package de.oopexpert.oopdi.exception;

/**
 * Thrown when instance destruction completes with failures: shutdown
 * ({@code Context.shutdown()}) or request-chain end
 * ({@code RequestScopeManager}). Destruction always continues best-effort
 * with all remaining instances; the individual failures are aggregated as
 * suppressed exceptions instead of aborting the process.
 */
public class DestructionFailed extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public DestructionFailed(String message) {
		super(message);
	}

}
