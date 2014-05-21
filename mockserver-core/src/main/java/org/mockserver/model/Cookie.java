package org.mockserver.model;

import java.util.Collection;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class Cookie extends KeyToMultiValue {

    public static Cookie cookie(String name, String... value) {
        return new Cookie(name, value);
    }

    public static Cookie cookie(String name, List<String> value) {
        return new Cookie(name, value);
    }

    public Cookie(String name, String... value) {
        super(name, value);
    }

    public Cookie(String name, Collection<String> value) {
        super(name, value);
    }
}
