package de.oopexpert.oopdi.metadata;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.oopdi.ClasspathScanner;
import de.oopexpert.oopdi.InjectableFilter;
import de.oopexpert.teststructure.ClassMultipleConstructorsWithSideEffect;

/**
 * Unit tests for {@link MetadataWarmup} in isolation (not going through {@code OOPDI}), so that a
 * job-level classpath-scan failure can be simulated deterministically via a broken
 * {@link ClasspathScanner} subclass.
 */
class TestMetadataWarmup {

    @Test
    void testRequiresWarmupEnabledMode() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new MetadataWarmup(new ClasspathScanner(), new InjectableFilter(), new MetadataRepository(MetadataMode.DISABLED), MetadataMode.DISABLED));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new MetadataWarmup(new ClasspathScanner(), new InjectableFilter(), new MetadataRepository(MetadataMode.METADATA_ONLY), MetadataMode.METADATA_ONLY));
    }

    @Test
    void testSuccessfulScanReachesReady() {
        MetadataWarmup warmup = new MetadataWarmup(new ClasspathScanner(), new InjectableFilter(),
            new MetadataRepository(MetadataMode.WARMUP_FAIL_FAST), MetadataMode.WARMUP_FAIL_FAST);

        warmup.start();

        WarmupStatus terminal = awaitTerminalStatus(warmup::getStatus);

        Assertions.assertEquals(WarmupStatus.READY, terminal,
            "A single misbehaving candidate class (e.g. multiple constructors) must not fail the whole job");
        Assertions.assertTrue(warmup.getFailureCause().isEmpty());
    }

    @Test
    void testJobLevelScanFailureSetsFailedStatusAndCapturesCause() {
        ClasspathScanner brokenScanner = new ClasspathScanner() {
            @Override
            public Set<Class<?>> findAllAnnotatedClasses(Class<? extends Annotation> annotation) {
                throw new RuntimeException("Simulated classpath scan failure");
            }
        };

        MetadataWarmup warmup = new MetadataWarmup(brokenScanner, new InjectableFilter(),
            new MetadataRepository(MetadataMode.WARMUP_FAIL_FAST), MetadataMode.WARMUP_FAIL_FAST);

        warmup.start();

        WarmupStatus terminal = awaitTerminalStatus(warmup::getStatus);

        Assertions.assertEquals(WarmupStatus.FAILED, terminal);
        Assertions.assertTrue(warmup.getFailureCause().isPresent());
        Assertions.assertEquals("Simulated classpath scan failure", warmup.getFailureCause().get().getMessage());
    }

    @Test
    void testIndividualCandidateFailureDoesNotFailJob() {
        // ClassMultipleConstructorsWithSideEffect is @Injectable and on the test classpath, so a
        // real full scan will discover it and MetadataRepository.getMetadata will throw
        // MultipleConstructors for it internally -- that must be swallowed/logged, not propagated.
        Assertions.assertNotNull(ClassMultipleConstructorsWithSideEffect.class);

        MetadataWarmup warmup = new MetadataWarmup(new ClasspathScanner(), new InjectableFilter(),
            new MetadataRepository(MetadataMode.WARMUP_LENIENT), MetadataMode.WARMUP_LENIENT);

        warmup.start();

        WarmupStatus terminal = awaitTerminalStatus(warmup::getStatus);

        Assertions.assertEquals(WarmupStatus.READY, terminal);
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
}
