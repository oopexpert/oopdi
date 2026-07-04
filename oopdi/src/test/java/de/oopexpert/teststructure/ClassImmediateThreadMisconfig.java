package de.oopexpert.teststructure;

import de.oopexpert.oopdi.Scope;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable(scope = Scope.THREAD, immediate = true)
public class ClassImmediateThreadMisconfig {

    public int ping() {
        return 1;
    }

}
