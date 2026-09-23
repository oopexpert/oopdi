package de.oopexpert.teststructure;

import de.oopexpert.oopdi.VariableSource;
import de.oopexpert.oopdi.annotation.InjectVariable;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassBrokenSetImpl extends ClassBrokenSetBase {

    @InjectVariable(key = "definitelyNotSetKey_brokenSetElement", source = VariableSource.PARAMETER)
    private String missingValue;

    public String getMissingValue() {
        return missingValue;
    }

}
