package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.annotation.PreDestroy;

@Injectable
public class ClassWithPreDestroy {

    private boolean destroyed = false;

    @PreDestroy
    public void cleanup() {
        destroyed = true;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

}
