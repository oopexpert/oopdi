package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassProxyReturnTarget {

    public int add(int a, int b) {
        return a + b;
    }

}
