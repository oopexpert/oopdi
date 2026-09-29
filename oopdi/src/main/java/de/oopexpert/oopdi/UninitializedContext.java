package de.oopexpert.oopdi;

/**
 * Null object stand-in for a container whose {@code startup()} has not run yet: stateless
 * and immutable, hence safe to share and publish. Reports {@link ContainerStatus#NOT_STARTED},
 * ignores {@link #shutdown()} (shutdown before start is neutral), and fails loud on any bean
 * access — such calls must go through {@code OOPDI.getContext()}, which rejects them with a
 * dedicated exception before they could reach this object.
 */
final class UninitializedContext extends Context<Object> {

	private static final UninitializedContext INSTANCE = new UninitializedContext();

	private UninitializedContext() {
		super();
	}

	/**
	 * Shared null object for containers whose {@code startup()} has not run yet: stateless
	 * and immutable, hence safe to share across containers.
	 */
	@SuppressWarnings("unchecked")
	static <T> Context<T> instance() {
		return (Context<T>) INSTANCE;
	}

	@Override
	boolean isUninitialized() {
		return true;
	}

	@Override
	public ContainerStatus getStatus() {
		return ContainerStatus.NOT_STARTED;
	}

	@Override
	public void shutdown() {
		// Neutral no-op: shutdown before startup destroys nothing and records nothing.
	}

	@Override
	public <A> A getOrCreate(Class<A> clazz) {
		throw new IllegalStateException("UninitializedContext must never resolve beans; guard via OOPDI.getContext() instead.");
	}

	@Override
	public <A> A getOrCreateProxy(Class<A> clazz) {
		throw new IllegalStateException("UninitializedContext must never issue proxies; guard via OOPDI.getContext() instead.");
	}

	@Override
	public boolean isDirectConstructionPhase() {
		throw new IllegalStateException("UninitializedContext tracks no construction phase; guard via OOPDI.getContext() instead.");
	}

	@Override
	public void injectFields(Object instance) {
		throw new IllegalStateException("UninitializedContext injects no fields; guard via OOPDI.getContext() instead.");
	}

	@Override
	void validateGraph(Class<?> rootClazz) {
		throw new IllegalStateException("UninitializedContext validates no graph; guard via OOPDI.getContext() instead.");
	}
}
