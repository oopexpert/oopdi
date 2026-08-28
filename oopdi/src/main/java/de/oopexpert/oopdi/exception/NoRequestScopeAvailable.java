package de.oopexpert.oopdi.exception;

public class NoRequestScopeAvailable extends RuntimeException {

	private static final long serialVersionUID = 7234162147391182162L;

	public NoRequestScopeAvailable() {
		super("No REQUEST scope is active on this thread. REQUEST-scoped beans can only be accessed through a proxy call chain, not via a direct reference to the real object.");
	}

}
