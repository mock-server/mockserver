package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class Not extends ObjectWithReflectiveEqualsHashCodeToString {

    Boolean not;

    public static <T extends Not> T not(T t) {
        t.not = true;
        return t;
    }

    public Boolean isNot() {
        return not;
    }
}
