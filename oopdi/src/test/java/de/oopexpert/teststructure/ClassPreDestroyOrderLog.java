package de.oopexpert.teststructure;

import java.util.ArrayList;
import java.util.List;

import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassPreDestroyOrderLog {

    private final List<String> order = new ArrayList<>();

    public void record(String name) {
        order.add(name);
    }

    public List<String> getOrder() {
        return order;
    }

}
