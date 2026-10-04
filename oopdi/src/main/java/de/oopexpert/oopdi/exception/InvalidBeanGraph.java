package de.oopexpert.oopdi.exception;

import java.util.List;

/**
 * Thrown when dry-run startup validation ({@code OOPDI.validate()}) finds
 * structural wiring problems in the bean graph reachable from the root class.
 * Deliberately a standalone type (not grouped with the runtime injection
 * failures): validation creates nothing (no
 * constructor runs, no field is set), so an aggregate dry-run failure is a
 * different phase than a failed runtime injection attempt. The individual
 * findings are exposed via {@link #getProblems()}; per-problem causes stay
 * attached as suppressed exceptions.
 */
public class InvalidBeanGraph extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final List<String> problems;

	public InvalidBeanGraph(String message, List<String> problems) {
		super(message);
		this.problems = List.copyOf(problems);
	}

	/**
	 * The individual problem descriptions, in the same order as reported in
	 * the exception message. Unmodifiable.
	 */
	public List<String> getProblems() {
		return problems;
	}

}
