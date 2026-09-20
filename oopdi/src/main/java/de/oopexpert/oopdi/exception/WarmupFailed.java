package de.oopexpert.oopdi.exception;

/**
 * Thrown by {@code OOPDI.getInstance}/{@code getContext} when the background metadata warmup
 * job (see {@code metadata.MetadataWarmup}) failed and {@code MetadataMode#isFailFast()} is
 * {@code true} ({@link de.oopexpert.oopdi.metadata.MetadataMode#WARMUP_FAIL_FAST}). Under
 * {@link de.oopexpert.oopdi.metadata.MetadataMode#WARMUP_LENIENT} the same failure is only
 * logged and this exception is never thrown.
 */
public class WarmupFailed extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public WarmupFailed(String message, Throwable cause) {
		super(message, cause);
	}

}
