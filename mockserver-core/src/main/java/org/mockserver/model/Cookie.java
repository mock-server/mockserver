package org.mockserver.model;

import java.util.Collection;
import java.util.List;

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
