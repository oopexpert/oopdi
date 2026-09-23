package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.InjectInstance;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassBrokenProxiabilityRoot {

    @InjectInstance
    private ClassFinalBean finalBean;

    @InjectInstance
    private ClassPrivateCtor privateCtorBean;

    public void ping() {
    }

}
