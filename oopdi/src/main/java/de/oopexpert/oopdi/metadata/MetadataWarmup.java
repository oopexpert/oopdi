package de.oopexpert.oopdi.metadata;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.oopexpert.oopdi.ClasspathScanner;
import de.oopexpert.oopdi.InjectableFilter;
import de.oopexpert.oopdi.annotation.Injectable;

/**
 * Runs a one-shot background job (daemon thread, no retries — "fail is fail") that scans the
 * entire classpath for {@code @Injectable} classes and pre-populates {@link MetadataRepository}'s
 * cache for each of them, so that the first real {@code getInstance()} call for a given class
 * does not pay for classpath scanning and reflective introspection on the critical path.
 *
 * <p>A single candidate class failing metadata inspection (e.g. violating the "exactly one
 * constructor" rule) is logged and skipped; it does not fail the overall job — that class's real
 * failure surfaces later, synchronously, only if and when it is actually requested. Only a
 * failure of the classpath scan itself (the job as a whole, e.g. an unreadable classpath entry)
 * is reflected by {@link WarmupStatus#FAILED} and {@link #getFailureCause()}.</p>
 */
public class MetadataWarmup {

	private static final Logger log = LoggerFactory.getLogger(MetadataWarmup.class);

	private final ClasspathScanner scanner;
	private final InjectableFilter filter;
	private final MetadataRepository metadataRepository;

	private final AtomicReference<WarmupStatus> status = new AtomicReference<>(WarmupStatus.NOT_STARTED);
	private final AtomicReference<Throwable> failureCause = new AtomicReference<>();

	public MetadataWarmup(ClasspathScanner scanner, InjectableFilter filter, MetadataRepository metadataRepository, MetadataMode mode) {
		this.scanner = Objects.requireNonNull(scanner, "scanner must not be null");
		this.filter = Objects.requireNonNull(filter, "filter must not be null");
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository must not be null");
		if (!Objects.requireNonNull(mode, "mode must not be null").isWarmupEnabled()) {
			throw new IllegalArgumentException("MetadataWarmup requires a warmup-enabled MetadataMode, got " + mode);
		}
	}

	/**
	 * Starts the background scan on a daemon thread. May only be called once per instance; a
	 * second call fails fast instead of launching a duplicate scan.
	 */
	public void start() {
		if (!status.compareAndSet(WarmupStatus.NOT_STARTED, WarmupStatus.RUNNING)) {
			throw new IllegalStateException("MetadataWarmup has already been started (status: " + status.get() + ").");
		}
		Thread thread = new Thread(this::run, "oopdi-metadata-warmup");
		thread.setDaemon(true);
		thread.start();
	}

	private void run() {
		try {
			Set<Class<?>> candidates = scanner.findAllAnnotatedClasses(Injectable.class);
			Set<Class<?>> filtered = candidates.stream()
					.filter(filter)
					.collect(Collectors.toUnmodifiableSet());
			for (Class<?> candidate : filtered) {
				warmUpOne(candidate);
			}
			status.set(WarmupStatus.READY);
			log.debug("Metadata warmup completed: {} @Injectable classes inspected", filtered.size());
		} catch (RuntimeException e) {
			failureCause.set(e);
			status.set(WarmupStatus.FAILED);
			log.error("Metadata warmup failed: classpath scan for @Injectable classes threw", e);
		}
	}

	private void warmUpOne(Class<?> candidate) {
		try {
			metadataRepository.getMetadata(candidate);
		} catch (RuntimeException e) {
			log.warn("Metadata warmup: failed to pre-inspect class '{}'; will be re-attempted synchronously on first real use", candidate.getName(), e);
		}
	}

	public WarmupStatus getStatus() {
		return status.get();
	}

	public Optional<Throwable> getFailureCause() {
		return Optional.ofNullable(failureCause.get());
	}
}
