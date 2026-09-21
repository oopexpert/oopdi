package de.oopexpert.oopdi.exception;

/**
 * Thrown when the classpath itself cannot be scanned (unreadable entries, I/O failures) —
 * as opposed to a scan that completes but finds no (or ambiguous) candidates, which is
 * reported via {@code NoClassesLeftAfterFiltering} /
 * {@code MultipleClassesLeftAfterFiltering}. A scan failure is an infrastructure problem,
 * not a configuration problem.
 */
public class ClasspathScanFailed extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ClasspathScanFailed(String message, Throwable cause) {
		super(message, cause);
	}

}
