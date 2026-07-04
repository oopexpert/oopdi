package de.oopexpert.teststructure;

import de.oopexpert.oopdi.OOPDI;
import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.annotation.PostConstruct;

@Injectable
public class ClassPostConstructWithParameters {

    private boolean initialized;
    private boolean classAInjectedIntoPostConstruct;
    private boolean oopdiInjectedIntoPostConstruct;

    @PostConstruct
    public void init(ClassA classA, OOPDI<?> oopdi) {
        initialized = true;
        classAInjectedIntoPostConstruct = classA != null && classA.getDbUsername() != null;
        oopdiInjectedIntoPostConstruct = oopdi != null;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isClassAInjectedIntoPostConstruct() {
        return classAInjectedIntoPostConstruct;
    }

    public boolean isOopdiInjectedIntoPostConstruct() {
        return oopdiInjectedIntoPostConstruct;
    }

}
