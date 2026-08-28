package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.InjectInstance;
import de.oopexpert.oopdi.annotation.Injectable;
import de.oopexpert.oopdi.annotation.PreDestroy;

@Injectable
public class ClassPreDestroyOrderDependent {

    private final ClassPreDestroyOrderDependency dependency;

    @InjectInstance
    private ClassPreDestroyOrderLog log;

    public ClassPreDestroyOrderDependent(ClassPreDestroyOrderDependency dependency) {
        this.dependency = dependency;
    }

    public void ping() {
        // no-op; calling any method through the proxy triggers real object creation
    }

    @PreDestroy
    public void cleanup() {
        log.record("dependent");
    }

}
