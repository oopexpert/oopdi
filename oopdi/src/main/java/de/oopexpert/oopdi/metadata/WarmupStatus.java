package de.oopexpert.oopdi.metadata;

/**
 * Runtime status of the background metadata warmup job (see {@link MetadataWarmup}). This is
 * deliberately kept separate from {@link MetadataMode}: {@code MetadataMode} is a fixed
 * configuration choice made once at startup, while {@code WarmupStatus} is an observed value
 * that changes over time as the background job progresses.
 */
public enum WarmupStatus {

	/** No warmup was configured/started ({@link MetadataMode#isWarmupEnabled()} is {@code false}). */
	NOT_STARTED,

	/** The background classpath scan and cache pre-population is in progress. */
	RUNNING,

	/** The background job completed; the classpath scan itself succeeded (individual classes
	 * that failed metadata inspection were logged and skipped, see {@link MetadataMode}). */
	READY,

	/** The classpath scan itself failed; see {@link MetadataWarmup#getFailureCause()}. */
	FAILED
}
