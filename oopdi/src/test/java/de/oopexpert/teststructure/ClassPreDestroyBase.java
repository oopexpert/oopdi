package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.annotation.PreDestroy;

@Injectable
public abstract class ClassPreDestroyBase {

    private boolean baseDestroyed = false;

    @PreDestroy
    public void baseCleanup() {
        baseDestroyed = true;
    }

    public boolean isBaseDestroyed() {
        return baseDestroyed;
    }

}
