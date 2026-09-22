package de.oopexpert.teststructure;

import de.oopexpert.oopdi.VariableSource;
import de.oopexpert.oopdi.annotation.InjectInstance;
import de.oopexpert.oopdi.annotation.InjectVariable;
import de.oopexpert.oopdi.annotation.Injectable;

/**
 * Deliberately unwirable graph for startup validation: a constructor dependency leading into
 * a cycle, a field dependency on a non-bean type, and a variable injection with a missing key.
 * Never instantiated in tests (only dry-validated) — resolving it for real would fail.
 */
@Injectable
public class ClassBrokenGraphRoot {

    private final ClassBrokenCycleA cycleA;

    @InjectInstance
    private String name;

    @InjectVariable(key = "definitelyNotSetKey_brokenGraph", source = VariableSource.PARAMETER)
    private String missingValue;

    public ClassBrokenGraphRoot(ClassBrokenCycleA cycleA) {
        this.cycleA = cycleA;
    }

}
