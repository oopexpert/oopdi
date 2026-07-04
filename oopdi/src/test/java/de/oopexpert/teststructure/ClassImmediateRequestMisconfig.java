package de.oopexpert.teststructure;

import de.oopexpert.oopdi.Scope;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable(scope = Scope.REQUEST, immediate = true)
public class ClassImmediateRequestMisconfig {

    public int ping() {
        return 1;
    }

}
