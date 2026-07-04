package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.annotation.PostConstruct;

@Injectable
public abstract class ClassPostConstructBase {

    private boolean baseInitialized = false;

    @PostConstruct
    public void baseInit() {
        baseInitialized = true;
    }

    public boolean isBaseInitialized() {
        return baseInitialized;
    }

}
