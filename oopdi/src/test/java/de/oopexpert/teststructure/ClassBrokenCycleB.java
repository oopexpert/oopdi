package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassBrokenCycleB {

    private final ClassBrokenCycleA cycleA;

    public ClassBrokenCycleB(ClassBrokenCycleA cycleA) {
        this.cycleA = cycleA;
    }

    public ClassBrokenCycleA getCycleA() {
        return cycleA;
    }

}
