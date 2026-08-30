package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.InjectInstance;
import de.oopexpert.oopdi.annotation.Injectable;

/**
 * Used to verify that field processing during dependency injection only opens reflective
 * access ({@code Field#setAccessible(true)}) for fields that carry an inject annotation, not
 * for every declared field.
 */
@Injectable
public class ClassFieldAccessibilityTarget {

    @InjectInstance
    private ClassA injectedField;

    private String plainField = "unused";

    public ClassA getInjectedField() {
        return injectedField;
    }

}
