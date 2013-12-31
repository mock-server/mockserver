package org.mockserver.model;

import java.util.Collection;

/**
 * @author jamesdbloom
 */
public class Cookie extends KeyToMultiValue {

    public Cookie(String name, String... value) {
        super(name, value);
    }

    public Cookie(String name, Collection<String> value) {
        super(name, value);
    }
}
