package de.oopexpert.oopdi;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.oopexpert.oopdi.exception.CannotInject;
import de.oopexpert.teststructure.ClassBrokenFormatVar;
import de.oopexpert.teststructure.ClassBrokenGraphRoot;
import de.oopexpert.teststructure.ClassBrokenProxiabilityRoot;
import de.oopexpert.teststructure.ClassBrokenSetRoot;
import de.oopexpert.teststructure.ClassEmptyCharVar;
import de.oopexpert.teststructure.ClassFieldCycleA;
import de.oopexpert.teststructure.ClassFinalBean;
import de.oopexpert.teststructure.ClassImmediateLocalMisconfig;
import de.oopexpert.teststructure.ClassNestedOuter;
import de.oopexpert.teststructure.ClassPrivateCtor;
import de.oopexpert.teststructure.ClassUuidVar;

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

    @Test
    void testFinalClassIsReportedByValidationAndRuntime() {
        OOPDI<ClassBrokenProxiabilityRoot> oopdi = new OOPDI<>(ClassBrokenProxiabilityRoot.class);

        CannotInject validationFailure = Assertions.assertThrows(CannotInject.class, oopdi::validate,
            "A final managed class must fail startup validation");
        Assertions.assertTrue(validationFailure.getMessage().contains(ClassFinalBean.class.getName())
                && validationFailure.getMessage().contains("final"),
            "The final class must be named as unproxiable, but was: " + validationFailure.getMessage());

        // Runtime parity through the direct root: ByteBuddy cannot subclass it either —
        // but as CannotInject, not raw IllegalArgumentException.
        OOPDI<ClassFinalBean> direct = new OOPDI<>(ClassFinalBean.class);
        Assertions.assertThrows(CannotInject.class, () -> direct.getInstance(ClassFinalBean.class),
            "A final managed class must fail resolution descriptively at runtime, too");
    }

    @Test
    void testPrivateConstructorIsReportedByValidationAndRuntime() {
        OOPDI<ClassBrokenProxiabilityRoot> oopdi = new OOPDI<>(ClassBrokenProxiabilityRoot.class);

        CannotInject validationFailure = Assertions.assertThrows(CannotInject.class, oopdi::validate,
            "A private primary constructor must fail startup validation");
        Assertions.assertTrue(validationFailure.getMessage().contains(ClassPrivateCtor.class.getName())
                && validationFailure.getMessage().contains("private"),
            "The constructor visibility must be named, but was: " + validationFailure.getMessage());

        OOPDI<ClassPrivateCtor> direct = new OOPDI<>(ClassPrivateCtor.class);
        Assertions.assertThrows(CannotInject.class, () -> direct.getInstance(ClassPrivateCtor.class),
            "A private primary constructor must fail resolution descriptively at runtime, too");
    }

    @Test
    void testEmptyVariableValueIsReportedByValidationAndRuntime() {
        try (TestSystemProperties.Scope ignored = TestSystemProperties.withProperties(
                Map.of("definitelySetKey_emptyChar", ""))) {
            OOPDI<ClassEmptyCharVar> oopdi = new OOPDI<>(ClassEmptyCharVar.class);

            CannotInject validationFailure = Assertions.assertThrows(CannotInject.class, oopdi::validate,
                "An empty variable value for a char field must fail startup validation");
            Assertions.assertTrue(validationFailure.getMessage().contains("definitelySetKey_emptyChar"),
                "The key must be named, but was: " + validationFailure.getMessage());

            Assertions.assertThrows(CannotInject.class, () -> oopdi.getInstance(ClassEmptyCharVar.class).getValue(),
                "An empty variable value for a char field must fail resolution descriptively at runtime, too");
        }
    }

    @Test
    void testUnassignableVariableValueIsReportedByValidationAndRuntime() {
        OOPDI<ClassUuidVar> oopdi = new OOPDI<>(ClassUuidVar.class);

        CannotInject validationFailure = Assertions.assertThrows(CannotInject.class, oopdi::validate,
            "A variable value of the wrong type must fail startup validation");
        Assertions.assertTrue(validationFailure.getMessage().contains("not assignable"),
            "The assignability problem must be named, but was: " + validationFailure.getMessage());

        Assertions.assertThrows(CannotInject.class, () -> oopdi.getInstance(ClassUuidVar.class).getId(),
            "A variable value of the wrong type must fail resolution descriptively at runtime, too");
    }
}
