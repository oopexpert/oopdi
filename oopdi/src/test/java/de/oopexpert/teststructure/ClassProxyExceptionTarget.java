package de.oopexpert.teststructure;

import de.oopexpert.oopdi.annotation.Injectable;

@Injectable
public class ClassProxyExceptionTarget {

    public void fail() {
        throw new IllegalArgumentException("boom");
    }

}
