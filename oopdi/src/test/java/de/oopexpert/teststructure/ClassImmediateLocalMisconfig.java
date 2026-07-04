package de.oopexpert.teststructure;

import de.oopexpert.oopdi.Scope;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable(scope = Scope.LOCAL, immediate = true)
public class ClassImmediateLocalMisconfig {

    public int ping() {
        return 1;
    }

}
