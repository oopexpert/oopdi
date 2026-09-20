package de.oopexpert.oopdi.metadata;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.oopdi.TestSystemProperties;

class TestMetadataMode {

    @Test
    void testAbsentPropertyDefaultsToDisabled() {
        String previous = System.getProperty(MetadataMode.SYSTEM_PROPERTY);
        System.clearProperty(MetadataMode.SYSTEM_PROPERTY);
        try {
            Assertions.assertEquals(MetadataMode.DISABLED, MetadataMode.fromSystemProperty());
        } finally {
            if (previous != null) {
                System.setProperty(MetadataMode.SYSTEM_PROPERTY, previous);
            }
        }
    }

    @Test
    void testValidValuesParseCorrectly() {
        for (MetadataMode mode : MetadataMode.values()) {
            try (var ignored = TestSystemProperties.withProperties(Map.of(MetadataMode.SYSTEM_PROPERTY, mode.name()))) {
                Assertions.assertEquals(mode, MetadataMode.fromSystemProperty());
            }
        }
    }

    @Test
    void testInvalidValueThrows() {
        try (var ignored = TestSystemProperties.withProperties(Map.of(MetadataMode.SYSTEM_PROPERTY, "NotAValidMode"))) {
            Assertions.assertThrows(RuntimeException.class, MetadataMode::fromSystemProperty,
                "An unrecognized value must fail fast rather than silently fall back to a default");
        }
    }

    @Test
    void testDerivedFlags() {
        Assertions.assertFalse(MetadataMode.DISABLED.isCacheEnabled());
        Assertions.assertFalse(MetadataMode.DISABLED.isWarmupEnabled());
        Assertions.assertFalse(MetadataMode.DISABLED.isFailFast());

        Assertions.assertTrue(MetadataMode.METADATA_ONLY.isCacheEnabled());
        Assertions.assertFalse(MetadataMode.METADATA_ONLY.isWarmupEnabled());
        Assertions.assertFalse(MetadataMode.METADATA_ONLY.isFailFast());

        Assertions.assertTrue(MetadataMode.WARMUP_FAIL_FAST.isCacheEnabled());
        Assertions.assertTrue(MetadataMode.WARMUP_FAIL_FAST.isWarmupEnabled());
        Assertions.assertTrue(MetadataMode.WARMUP_FAIL_FAST.isFailFast());

        Assertions.assertTrue(MetadataMode.WARMUP_LENIENT.isCacheEnabled());
        Assertions.assertTrue(MetadataMode.WARMUP_LENIENT.isWarmupEnabled());
        Assertions.assertFalse(MetadataMode.WARMUP_LENIENT.isFailFast());
    }
}
