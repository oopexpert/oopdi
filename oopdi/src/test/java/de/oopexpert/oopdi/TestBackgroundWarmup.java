package de.oopexpert.oopdi;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.oopdi.exception.WarmupFailed;
import de.oopexpert.oopdi.metadata.MetadataMode;
import de.oopexpert.oopdi.metadata.WarmupStatus;
import de.oopexpert.teststructure.ClassA;

/**
 * Verifies OOPDI's wiring of {@link MetadataMode}/background metadata warmup: default behavior
 * is unchanged, an invalid mode value fails fast at first use (lazy init), a successful warmup reaches
 * {@link WarmupStatus#READY} without affecting normal bean resolution, and a job-level warmup
 * failure is either surfaced (WARMUP_FAIL_FAST) or silently tolerated via the existing on-demand
 * fallback (WARMUP_LENIENT).
 */
class TestBackgroundWarmup {

    @Test
    void testDefaultModeIsDisabledAndBehaviorIsUnchanged() {
        String previous = System.getProperty(MetadataMode.SYSTEM_PROPERTY);
        System.clearProperty(MetadataMode.SYSTEM_PROPERTY);
        try {
            OOPDI<ClassA> oopdi = new OOPDI<>(ClassA.class);

            Assertions.assertEquals(WarmupStatus.NOT_STARTED, oopdi.getWarmupStatus());
            Assertions.assertNotNull(oopdi.getInstance(ClassA.class));
        } finally {
            if (previous != null) {
                System.setProperty(MetadataMode.SYSTEM_PROPERTY, previous);
            }
        }
    }

    @Test
    void testInvalidModeFailsFastAtFirstUse() {
        try (var ignored = TestSystemProperties.withProperties(Map.of(MetadataMode.SYSTEM_PROPERTY, "NotAValidMode"))) {
            OOPDI<ClassA> oopdi = new OOPDI<>(ClassA.class);
            Assertions.assertThrows(RuntimeException.class, oopdi::getWarmupStatus);
            Assertions.assertThrows(RuntimeException.class, () -> oopdi.getInstance(ClassA.class));
        }
    }

    @Test
    void testMetadataOnlyModeDoesNotStartWarmup() {
        try (var ignored = TestSystemProperties.withProperties(Map.of(MetadataMode.SYSTEM_PROPERTY, "METADATA_ONLY"))) {
            OOPDI<ClassA> oopdi = new OOPDI<>(ClassA.class);

            Assertions.assertEquals(WarmupStatus.NOT_STARTED, oopdi.getWarmupStatus());
            Assertions.assertNotNull(oopdi.getInstance(ClassA.class));
        }
    }

    @Test
    void testWarmupFailFastModeReachesReadyAndWorksNormally() {
        try (var ignored = TestSystemProperties.withProperties(Map.of(MetadataMode.SYSTEM_PROPERTY, "WARMUP_FAIL_FAST"))) {
            OOPDI<ClassA> oopdi = new OOPDI<>(ClassA.class);

            WarmupStatus terminal = awaitTerminalStatus(oopdi::getWarmupStatus);

            Assertions.assertEquals(WarmupStatus.READY, terminal);
            Assertions.assertNotNull(oopdi.getInstance(ClassA.class));
        }
    }

    @Test
    void testWarmupFailFastModePropagatesJobLevelFailureOnNextGetInstance() {
        try (var ignored = TestSystemProperties.withProperties(Map.of(MetadataMode.SYSTEM_PROPERTY, "WARMUP_FAIL_FAST"))) {
            OOPDI<ClassA> oopdi = new OOPDI<>(ClassA.class);
            injectWarmupScanner(oopdi, new BrokenClasspathScanner());

            awaitTerminalStatus(oopdi::getWarmupStatus);

            Assertions.assertEquals(WarmupStatus.FAILED, oopdi.getWarmupStatus());
            Assertions.assertThrows(WarmupFailed.class, () -> oopdi.getInstance(ClassA.class));
        }
    }

    @Test
    void testWarmupLenientModeFallsBackOnJobLevelFailure() {
        try (var ignored = TestSystemProperties.withProperties(Map.of(MetadataMode.SYSTEM_PROPERTY, "WARMUP_LENIENT"))) {
            OOPDI<ClassA> oopdi = new OOPDI<>(ClassA.class);
            injectWarmupScanner(oopdi, new BrokenClasspathScanner());

            awaitTerminalStatus(oopdi::getWarmupStatus);

            Assertions.assertEquals(WarmupStatus.FAILED, oopdi.getWarmupStatus());
            Assertions.assertNotNull(oopdi.getInstance(ClassA.class),
                "WARMUP_LENIENT must keep working via the on-demand fallback despite a failed background scan");
        }
    }

    private static WarmupStatus awaitTerminalStatus(Supplier<WarmupStatus> statusSupplier) {
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        WarmupStatus status;
        do {
            status = statusSupplier.get();
            if (status == WarmupStatus.READY || status == WarmupStatus.FAILED) {
                return status;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        } while (System.nanoTime() < deadlineNanos);
        throw new AssertionError("Warmup did not reach a terminal status within timeout, last status=" + status);
    }

    /**
     * Test-only injection of the warmup scanner: must run before the first call that
     * triggers {@code getMetadataWarmup()} (i.e. before {@code getWarmupStatus()},
     * {@code getInstance()} or {@code validate()}), otherwise the lazy getter has
     * already cached the default scanner and the injection has no effect.
     */
    private static void injectWarmupScanner(OOPDI<?> oopdi, ClasspathScanner scanner) {
        try {
            var field = OOPDI.class.getDeclaredField("warmupScanner");
            field.setAccessible(true);
            field.set(oopdi, scanner);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot inject warmupScanner for test", e);
        }
    }

    private static final class BrokenClasspathScanner extends ClasspathScanner {
        @Override
        public Set<Class<?>> findAllAnnotatedClasses(Class<? extends java.lang.annotation.Annotation> annotation) {
            throw new RuntimeException("Simulated classpath scan failure");
        }
    }
}
