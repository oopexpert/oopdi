package de.oopexpert.teststructure;

import java.util.Set;

import de.oopexpert.oopdi.annotation.InjectSet;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassBrokenSetRoot {

    @InjectSet(hint = ClassBrokenSetBase.class)
    private Set<ClassBrokenSetBase> elements;

    public Set<ClassBrokenSetBase> getElements() {
        return elements;
    }

}
