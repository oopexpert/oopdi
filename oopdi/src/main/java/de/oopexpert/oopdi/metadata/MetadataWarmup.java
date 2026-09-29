package de.oopexpert.oopdi.metadata;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
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
 *
 * <p>For warmup-disabled modes ({@code DISABLED}/{@code METADATA_ONLY}) use the shared null
 * object {@link #disabled()}: it permanently reports {@link WarmupStatus#NOT_STARTED} and
 * ignores {@link #start()}, so callers never need a {@code null} branch.</p>
 */
public class MetadataWarmup {

	private static final Logger log = LoggerFactory.getLogger(MetadataWarmup.class);

	private final ClasspathScanner scanner;
	private final InjectableFilter filter;
	private final MetadataRepository metadataRepository;
	private final boolean disabled;

	private final AtomicReference<WarmupStatus> status = new AtomicReference<>(WarmupStatus.NOT_STARTED);
	private final AtomicReference<Throwable> failureCause = new AtomicReference<>();
	private final CountDownLatch completed = new CountDownLatch(1);

	public MetadataWarmup(ClasspathScanner scanner, InjectableFilter filter, MetadataRepository metadataRepository, MetadataMode mode) {
		this.scanner = Objects.requireNonNull(scanner, "scanner must not be null");
		this.filter = Objects.requireNonNull(filter, "filter must not be null");
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository must not be null");
		if (!Objects.requireNonNull(mode, "mode must not be null").isWarmupEnabled()) {
			throw new IllegalArgumentException("MetadataWarmup requires a warmup-enabled MetadataMode, got %s.".formatted(mode));
		}
		this.disabled = false;
	}

	private MetadataWarmup() {
		this.scanner = null;
		this.filter = null;
		this.metadataRepository = null;
		this.disabled = true;
	}

	/**
	 * Shared null object for warmup-disabled modes: stateless and immutable, hence safe to
	 * share across containers. Permanently reports {@link WarmupStatus#NOT_STARTED};
	 * {@link #start()} is a no-op and {@link #getFailureCause()} is always empty.
	 */
	public static MetadataWarmup disabled() {
		return DisabledHolder.INSTANCE;
	}

	private static final class DisabledHolder {
		private static final MetadataWarmup INSTANCE = new MetadataWarmup();
	}

	/**
	 * Starts the background scan on a daemon thread. May only be called once per instance; a
	 * second call fails fast instead of launching a duplicate scan. No-op on the
	 * {@link #disabled()} null object.
	 */
	public void start() {
		if (disabled) {
			return;
		}
		if (!status.compareAndSet(WarmupStatus.NOT_STARTED, WarmupStatus.RUNNING)) {
			throw new IllegalStateException("MetadataWarmup has already been started (status: %s).".formatted(status.get()));
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
		} catch (Throwable e) {
			// Deliberately Throwable: any job-level failure (including an Error from class
			// loading) must surface as FAILED instead of dying silently with status RUNNING.
			failureCause.set(e);
			status.set(WarmupStatus.FAILED);
			log.error("Metadata warmup failed: classpath scan for @Injectable classes threw", e);
		} finally {
			completed.countDown();
		}
	}

	private void warmUpOne(Class<?> candidate) {
		try {
			metadataRepository.getMetadata(candidate);
		} catch (Throwable e) {
			// Deliberately Throwable: a single unloadable candidate (including LinkageError
			// variants beyond the scanner's own filtering) must not fail the whole job.
			log.warn("Metadata warmup: failed to pre-inspect class '{}'; will be re-attempted synchronously on first real use", candidate.getName(), e);
		}
	}

	/**
	 * Blocks until the background job reached a terminal status ({@link WarmupStatus#READY} or
	 * {@link WarmupStatus#FAILED}). Returns immediately on the {@link #disabled()} null object
	 * and when the job already finished. Guaranteed to terminate: {@link #run()} funnels every
	 * outcome (including {@code Throwable}) through a terminal status before counting down.
	 *
	 * @throws IllegalStateException if the waiting thread is interrupted; the interrupt flag
	 *         is restored before throwing.
	 */
	public void awaitCompletion() {
		if (disabled) {
			return;
		}
		try {
			completed.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for metadata warmup completion.", e);
		}
	}

	public WarmupStatus getStatus() {
		if (disabled) {
			return WarmupStatus.NOT_STARTED;
		}
		return status.get();
	}

	public Optional<Throwable> getFailureCause() {
		if (disabled) {
			return Optional.empty();
		}
		return Optional.ofNullable(failureCause.get());
	}
}
