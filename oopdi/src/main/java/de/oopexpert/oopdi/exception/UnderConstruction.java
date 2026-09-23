package de.oopexpert.oopdi.exception;

public class UnderConstruction extends RuntimeException {

	public UnderConstruction(String string) {
		super(string);
	}

	/**
	 * @deprecated Never constructed with a cause internally; use
	 *             {@link #UnderConstruction(String)} instead. External construction of
	 *             framework-internal sentinel exceptions is unsupported. To be removed in 1.0.
	 */
	@Deprecated(forRemoval = true)
	public UnderConstruction(String string, UnderConstruction cd) {
		super(string, cd);
	}

	private static final long serialVersionUID = 2028845835382196862L;

}
