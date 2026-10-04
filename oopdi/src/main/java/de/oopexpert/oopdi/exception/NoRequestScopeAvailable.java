package de.oopexpert.oopdi.exception;

public class NoRequestScopeAvailable extends RuntimeException {

	private static final long serialVersionUID = 7234162147391182162L;

	public NoRequestScopeAvailable() {
		super("No REQUEST scope is active on this thread. REQUEST-scoped beans can only be resolved while a proxy call chain is active on this thread.");
	}

}
