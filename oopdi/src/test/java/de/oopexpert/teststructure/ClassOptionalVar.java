package de.oopexpert.teststructure;

import de.oopexpert.oopdi.VariableSource;
import de.oopexpert.oopdi.annotation.InjectVariable;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassOptionalVar {

    @InjectVariable(key = "definitelyNotSetKey_optional", source = VariableSource.PARAMETER, optional = true)
    private String optionalValue;

    @InjectVariable(key = "definitelyNotSetKey_default", source = VariableSource.PARAMETER, defaultValue = "fallback")
    private String defaultValue;

    @InjectVariable(key = "definitelyNotSetKey_defaultInt", source = VariableSource.PARAMETER, defaultValue = "42")
    private int defaultInt;

    public String getOptionalValue() {
        return optionalValue;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public int getDefaultInt() {
        return defaultInt;
    }

}
