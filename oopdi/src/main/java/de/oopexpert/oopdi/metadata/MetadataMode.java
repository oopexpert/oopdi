package de.oopexpert.oopdi.metadata;

import java.util.Arrays;

/**
 * Controls how {@link MetadataRepository} handles reflective class metadata (constructors,
 * field injection points, lifecycle methods):
 *
 * <ul>
 *   <li>{@link #DISABLED} — no caching; every {@link MetadataRepository#getMetadata(Class)} call
 *       re-inspects the class via reflection. This is the default when the system property is
 *       not set.</li>
 *   <li>{@link #METADATA_ONLY} — caching is enabled, populated lazily on first access
 *       (on-demand), no background work.</li>
 *   <li>{@link #WARMUP_FAIL_FAST} — caching is enabled and a background thread eagerly scans the
 *       classpath for all {@code @Injectable} classes to pre-populate the cache. If that
 *       classpath-wide scan itself fails, the next call to {@code OOPDI.getInstance}/{@code
 *       getContext} throws {@link de.oopexpert.oopdi.exception.WarmupFailed}.</li>
 *   <li>{@link #WARMUP_LENIENT} — same background warmup as {@link #WARMUP_FAIL_FAST}, but a
 *       failed scan is only logged; the framework keeps working via the existing on-demand
 *       fallback (useful when the warmup failing must not prevent e.g. a web server from starting
 *       up).</li>
 * </ul>
 *
 * Individual classes that fail metadata inspection during a background warmup (for example a
 * class violating the "exactly one constructor" rule) are logged and skipped; they do not affect
 * the overall warmup outcome. Only a failure of the classpath scan itself (the job as a whole)
 * is reflected by {@link #isFailFast()}/the warmup's {@code WarmupStatus}.
 */
public enum MetadataMode {

	DISABLED,
	METADATA_ONLY,
	WARMUP_FAIL_FAST,
	WARMUP_LENIENT;

	public static final String SYSTEM_PROPERTY = "oopdi.metadata.mode";

	public boolean isCacheEnabled() {
		return this != DISABLED;
	}

	public boolean isWarmupEnabled() {
		return this == WARMUP_FAIL_FAST || this == WARMUP_LENIENT;
	}

	public boolean isFailFast() {
		return this == WARMUP_FAIL_FAST;
	}

	/**
	 * Reads and parses {@link #SYSTEM_PROPERTY}. Absence of the property is not an error and
	 * yields {@link #DISABLED} (the default, matching pre-existing behavior). A value that does
	 * not match any constant name is a misconfiguration and fails fast.
	 */
	public static MetadataMode fromSystemProperty() {
		String value = System.getProperty(SYSTEM_PROPERTY);
		if (value == null) {
			return DISABLED;
		}
		try {
			return MetadataMode.valueOf(value.trim());
		} catch (IllegalArgumentException e) {
			throw new RuntimeException("Invalid value '" + value + "' for system property '"
					+ SYSTEM_PROPERTY + "'. Expected one of " + Arrays.toString(values()) + ".", e);
		}
	}
}
