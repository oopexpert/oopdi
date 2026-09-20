package de.oopexpert.oopdi;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TestSystemProperties {

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    private TestSystemProperties() {
    }

    public static Scope withProperties(Map<String, String> newValues) {
        Map<String, String> oldValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : newValues.entrySet()) {
            String key = entry.getKey();
            oldValues.put(key, System.getProperty(key));
            System.setProperty(key, entry.getValue());
        }

        return () -> {
            for (Map.Entry<String, String> entry : oldValues.entrySet()) {
                String key = entry.getKey();
                String oldValue = entry.getValue();
                if (oldValue == null) {
                    System.clearProperty(key);
                } else {
                    System.setProperty(key, oldValue);
                }
            }
        };
    }

}