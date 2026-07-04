package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassParallelA {

    public ClassParallelA() {
        ClassParallelInitTracker.onConstructorEnterAndAwaitRelease();
    }

    public void touch() {
        // no-op, invocation is used to trigger real object creation through proxy
    }

}
