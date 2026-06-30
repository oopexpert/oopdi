package de.oopexpert.teststructure;

import de.oopexpert.oopdi.VariableSource;
import de.oopexpert.oopdi.annotation.InjectVariable;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassMissingVar {

    @InjectVariable(key = "definitelyNotSetKey_12345", source = VariableSource.PARAMETER)
    private String missingValue;

    public String getMissingValue() {
        return missingValue;
    }

}
