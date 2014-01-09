package org.mockserver.model;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class Parameter extends KeyToMultiValue {

    public Parameter(String name, String... value) {
        super(name, value);
    }

    public Parameter(String name, List<String> value) {
        super(name, value);
    }
}
