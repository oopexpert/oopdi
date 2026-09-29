package de.oopexpert.oopdi;

/**
 * Runtime status of the container lifecycle (see {@link OOPDI#startup(boolean)} and
 * {@code Context.shutdown()}), mirroring the {@code metadata.WarmupStatus} pattern: a fixed
 * lifecycle choice would not help here, so this is an observed value that changes over time
 * as the container moves from startup through shutdown.
 *
 * <ul>
 *   <li>{@link #NOT_STARTED} — {@code startup()} has not been called yet; bean access fails
 *       fast, {@code shutdown()} is a neutral no-op.</li>
 *   <li>{@link #ACTIVE} — the container serves requests normally.</li>
 *   <li>{@link #SHUTTING_DOWN} — shutdown has started; no new bean creation is accepted anymore
 *       (requests fail fast), in-flight creations are drained.</li>
 *   <li>{@link #SHUTDOWN} — shutdown completed; every known instance received its
 *       {@code @PreDestroy} call.</li>
 *   <li>{@link #FAILED} — shutdown completed, but at least one {@code @PreDestroy} invocation
 *       failed. Destruction still continued best-effort for all remaining instances; the
 *       individual failures are aggregated as suppressed exceptions on the thrown error.</li>
 * </ul>
 */
public enum ContainerStatus {

	NOT_STARTED,
	ACTIVE,
	SHUTTING_DOWN,
	SHUTDOWN,
	FAILED
}
