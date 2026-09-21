package de.oopexpert.teststructure;

import de.oopexpert.oopdi.VariableSource;
import de.oopexpert.oopdi.annotation.InjectVariable;
import de.oopexpert.oopdi.annotation.Injectable;

/**
 * Declares an {@code optional} variable injection into a primitive field with no default.
 * Primitives cannot hold {@code null}, so resolving this must fail with a descriptive error
 * (instead of a cryptic reflective failure downstream). Kept separate from
 * {@link ClassOptionalVar} so the existing optional/default tests stay unaffected.
 */
@Injectable
public class ClassOptionalPrimitiveVar {

    @InjectVariable(key = "definitelyNotSetKey_optionalPrimitive", source = VariableSource.PARAMETER, optional = true)
    private int optionalInt;

    public int getOptionalInt() {
        return optionalInt;
    }

}
