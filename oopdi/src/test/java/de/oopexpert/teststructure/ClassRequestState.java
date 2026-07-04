package de.oopexpert.teststructure;

import java.util.concurrent.atomic.AtomicInteger;

import de.oopexpert.oopdi.Scope;
import de.oopexpert.oopdi.annotation.Injectable;

@Injectable(scope = Scope.REQUEST)
public class ClassRequestState {

    private static final AtomicInteger INSTANCE_SEQUENCE = new AtomicInteger(0);

    private int value;
    private final int id = INSTANCE_SEQUENCE.incrementAndGet();

    public static void resetCounter() {
        INSTANCE_SEQUENCE.set(0);
    }

    public int getId() {
        return id;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

}
