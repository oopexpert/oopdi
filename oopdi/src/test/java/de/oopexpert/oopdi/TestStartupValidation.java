package de.oopexpert.oopdi;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.oopdi.exception.CannotInject;
import de.oopexpert.teststructure.ClassBrokenGraphRoot;
import de.oopexpert.teststructure.ClassNestedOuter;

/**
 * Verifies opt-in startup validation ({@link OOPDI#validate()}): a dry run over the reachable
 * bean graph that creates nothing (no constructor runs, nothing injected) and aggregates every
 * structural wiring problem into a single {@code CannotInject} instead of failing
 * request-by-request at runtime.
 */
class TestStartupValidation {

    @Test
    void testBrokenGraphAggregatesAllProblems() {
        OOPDI<ClassBrokenGraphRoot> oopdi = new OOPDI<>(ClassBrokenGraphRoot.class);

        CannotInject ex = Assertions.assertThrows(CannotInject.class, oopdi::validate,
            "A broken wiring graph must fail startup validation");

        String message = ex.getMessage();
        Assertions.assertTrue(message.contains("definitelyNotSetKey_brokenGraph"),
            "Missing variable key must be reported, but was: " + message);
        Assertions.assertTrue(message.contains("java.lang.String"),
            "Non-bean field dependency must be reported, but was: " + message);
        Assertions.assertTrue(message.contains("ClassBrokenCycleA") && message.contains("ClassBrokenCycleB")
                && message.contains(" -> "),
            "Dependency cycle must be reported with path, but was: " + message);
        Assertions.assertFalse(ex.getSuppressed().length == 0,
            "Individual causes must be attached as suppressed exceptions");
    }

    @Test
    void testValidGraphValidatesSilently() {
        OOPDI<ClassNestedOuter> oopdi = new OOPDI<>(ClassNestedOuter.class);

        Assertions.assertDoesNotThrow(oopdi::validate,
            "A well-wired graph must validate silently");

        // Validation creates nothing: the graph still resolves normally afterwards.
        Assertions.assertNotNull(oopdi.getInstance(ClassNestedOuter.class).getDepA().getDepB());
    }

    @Test
    void testValidationIsRepeatable() {
        OOPDI<ClassNestedOuter> oopdi = new OOPDI<>(ClassNestedOuter.class);

        Assertions.assertDoesNotThrow(oopdi::validate);
        Assertions.assertDoesNotThrow(oopdi::validate,
            "Validation must hold no per-run state and stay repeatable");
    }
}
