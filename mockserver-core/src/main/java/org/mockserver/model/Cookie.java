package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class Cookie extends KeyAndValue {

    public static Cookie cookie(String name, String value) {
        return new Cookie(name, value);
    }

    public Cookie(String name, String value) {
        super(name, value);
    }
}
