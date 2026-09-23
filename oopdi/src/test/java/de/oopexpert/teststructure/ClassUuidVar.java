package de.oopexpert.teststructure;

import java.util.UUID;

import de.oopexpert.oopdi.VariableSource;
import de.oopexpert.oopdi.annotation.InjectVariable;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassUuidVar {

    @InjectVariable(key = "definitelyNotSetKey_uuid", source = VariableSource.PARAMETER, defaultValue = "not-a-uuid")
    private UUID id;

    public UUID getId() {
        return id;
    }

}
