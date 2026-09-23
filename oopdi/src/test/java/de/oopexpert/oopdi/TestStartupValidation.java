package de.oopexpert.oopdi;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.oopdi.exception.CannotInject;
import de.oopexpert.teststructure.ClassBrokenFormatVar;
import de.oopexpert.teststructure.ClassBrokenGraphRoot;
import de.oopexpert.teststructure.ClassBrokenSetRoot;
import de.oopexpert.teststructure.ClassFieldCycleA;
import de.oopexpert.teststructure.ClassImmediateLocalMisconfig;
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
        Assertions.assertTrue(message.contains("3 problem(s)"),
            "Exactly the three independent problems must be reported (no duplicates), but was: " + message);
    }

    @Test
    void testFieldCycleValidatesSilently() {
        OOPDI<ClassFieldCycleA> oopdi = new OOPDI<>(ClassFieldCycleA.class);

        // A pure field cycle resolves at runtime (early exposure of cached instances), so the
        // validator must stay silent as well — only constructor-edge loops deadlock.
        Assertions.assertDoesNotThrow(oopdi::validate,
            "A field-only dependency cycle must validate silently, mirroring runtime tolerance");

        // ...and the runtime really does build it (field cycles resolve via early exposure;
        // the chain only proves resolution succeeds instead of dying on a cycle error).
        Assertions.assertNotNull(oopdi.getInstance(ClassFieldCycleA.class).getCycleB().getCycleA());
    }

    @Test
    void testBrokenSetElementIsReported() {
        OOPDI<ClassBrokenSetRoot> oopdi = new OOPDI<>(ClassBrokenSetRoot.class);

        CannotInject ex = Assertions.assertThrows(CannotInject.class, oopdi::validate,
            "A broken @InjectSet element subgraph must fail startup validation");

        Assertions.assertTrue(ex.getMessage().contains("definitelyNotSetKey_brokenSetElement"),
            "Missing variable inside a set element must be reported, but was: " + ex.getMessage());
    }

    @Test
    void testImmediateMisconfigurationIsReported() {
        OOPDI<ClassImmediateLocalMisconfig> oopdi = new OOPDI<>(ClassImmediateLocalMisconfig.class);

        CannotInject ex = Assertions.assertThrows(CannotInject.class, oopdi::validate,
            "immediate=true on a non-GLOBAL bean must fail startup validation");

        Assertions.assertTrue(ex.getMessage().contains("Misconfiguration"),
            "The misconfiguration must be reported, but was: " + ex.getMessage());
    }

    @Test
    void testInvalidVariableFormatIsReported() {
        OOPDI<ClassBrokenFormatVar> oopdi = new OOPDI<>(ClassBrokenFormatVar.class);

        CannotInject ex = Assertions.assertThrows(CannotInject.class, oopdi::validate,
            "An unparsable variable value must fail startup validation");

        Assertions.assertTrue(ex.getMessage().contains("definitelyNotSetKey_brokenFormat")
                && ex.getMessage().contains("notANumber"),
            "Key and offending value must be reported, but was: " + ex.getMessage());
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
