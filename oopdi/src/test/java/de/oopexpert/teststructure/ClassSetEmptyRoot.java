package de.oopexpert.teststructure;

import java.util.Set;

import de.oopexpert.oopdi.annotation.InjectSet;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassSetEmptyRoot {

    @InjectSet(hint = ClassSetEmptyHint.class)
    private Set<ClassSetEmptyHint> values;

    public Set<ClassSetEmptyHint> getValues() {
        return values;
    }

}
