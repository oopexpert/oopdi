package de.oopexpert.teststructure;

import de.oopexpert.oopdi.VariableSource;
import de.oopexpert.oopdi.annotation.InjectVariable;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassEmptyCharVar {

    @InjectVariable(key = "definitelySetKey_emptyChar", source = VariableSource.PARAMETER)
    private char value;

    public char getValue() {
        return value;
    }

}
