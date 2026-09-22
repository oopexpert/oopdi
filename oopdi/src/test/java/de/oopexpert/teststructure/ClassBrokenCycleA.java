package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassBrokenCycleA {

    private final ClassBrokenCycleB cycleB;

    public ClassBrokenCycleA(ClassBrokenCycleB cycleB) {
        this.cycleB = cycleB;
    }

    public ClassBrokenCycleB getCycleB() {
        return cycleB;
    }

}
