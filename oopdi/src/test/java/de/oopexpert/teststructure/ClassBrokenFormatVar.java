package de.oopexpert.teststructure;

import de.oopexpert.oopdi.VariableSource;
import de.oopexpert.oopdi.annotation.InjectVariable;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassBrokenFormatVar {

    @InjectVariable(key = "definitelyNotSetKey_brokenFormat", source = VariableSource.PARAMETER, defaultValue = "notANumber")
    private int value;

    public int getValue() {
        return value;
    }

}
