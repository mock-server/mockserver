package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class Not extends ObjectWithJsonToString {

    Boolean not;

    public static <T extends Not> T not(T t) {
        t.not = true;
        return t;
    }

    public boolean isNot() {
        return not != null && not;
    }

    public Boolean getNot() { return not; }
}
