package de.oopexpert.teststructure;

import de.oopexpert.oopdi.VariableSource;
import de.oopexpert.oopdi.annotation.InjectVariable;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassVariableInvalidFormat {

    @InjectVariable(key = "matrix.invalid.int", source = VariableSource.PARAMETER)
    private int invalidInt;

    public int getInvalidInt() {
        return invalidInt;
    }

}
