package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.InjectInstance;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassNestedDepA {

    @InjectInstance
    private ClassNestedDepB depB;

    public ClassNestedDepB getDepB() {
        return depB;
    }

}
