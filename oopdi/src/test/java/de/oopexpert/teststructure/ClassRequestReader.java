package de.oopexpert.teststructure;

import de.oopexpert.oopdi.Scope;
import de.oopexpert.oopdi.annotation.InjectInstance;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable(scope = Scope.REQUEST)
public class ClassRequestReader {

    @InjectInstance
    private ClassRequestState state;

    public int readValue() {
        return state.getValue();
    }

    public int readStateId() {
        return state.getId();
    }

}
