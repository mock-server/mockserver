package org.mockserver.model;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class Cookie extends KeyToMultiValue<String, String> {

    public Cookie(String name, String... value) {
        super(name, value);
    }

    public Cookie(String name, List<String> value) {
        super(name, value);
    }
}
