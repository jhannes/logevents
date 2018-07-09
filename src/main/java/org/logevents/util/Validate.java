package org.logevents.util;

public class Validate {

    public static <T> T notNull(T o, String variableName) {
        if (o == null) {
            throw new IllegalArgumentException(variableName + " should not be null");
        }
        return o;
    }

}
