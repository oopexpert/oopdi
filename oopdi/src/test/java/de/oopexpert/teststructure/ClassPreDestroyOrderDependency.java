package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.InjectInstance;
import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.annotation.PreDestroy;

@Injectable
public class ClassPreDestroyOrderDependency {

    @InjectInstance
    private ClassPreDestroyOrderLog log;

    @PreDestroy
    public void cleanup() {
        log.record("dependency");
    }

}
