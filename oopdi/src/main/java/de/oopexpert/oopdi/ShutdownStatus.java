package de.oopexpert.oopdi;

/**
 * Runtime status of the container shutdown (see {@code Context.shutdown()}), mirroring the
 * {@code metadata.WarmupStatus} pattern: a fixed lifecycle choice would not help here, so this is
 * an observed value that changes over time as shutdown progresses.
 *
 * <ul>
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
public enum ShutdownStatus {

	ACTIVE,
	SHUTTING_DOWN,
	SHUTDOWN,
	FAILED
}
