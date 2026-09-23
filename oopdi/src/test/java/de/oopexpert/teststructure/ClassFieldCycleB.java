package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.InjectInstance;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassFieldCycleB {

    @InjectInstance
    private ClassFieldCycleA cycleA;

    public ClassFieldCycleA getCycleA() {
        return cycleA;
    }

}
