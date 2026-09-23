package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.InjectInstance;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassFieldCycleA {

    @InjectInstance
    private ClassFieldCycleB cycleB;

    public ClassFieldCycleB getCycleB() {
        return cycleB;
    }

}
