package de.oopexpert.teststructure;

import java.util.Set;

import de.oopexpert.oopdi.annotation.InjectSet;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassSetSideEffectRoot {

    @InjectSet(hint = ClassSetSideEffectHint.class)
    private Set<ClassSetSideEffectHint> values;

    public Set<ClassSetSideEffectHint> getValues() {
        return values;
    }

}
