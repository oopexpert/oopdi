package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassNestedOuter {

    private final ClassNestedDepA depA;

    public ClassNestedOuter(ClassNestedDepA depA) {
        this.depA = depA;
    }

    public ClassNestedDepA getDepA() {
        return depA;
    }

}
