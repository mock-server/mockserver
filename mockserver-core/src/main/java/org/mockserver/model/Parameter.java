package org.mockserver.model;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class Parameter extends KeyToMultiValue {

    public static Parameter param(String name, String... value) {
        return new Parameter(name, value);
    }

    public static Parameter param(String name, List<String> value) {
        return new Parameter(name, value);
    }

    public Parameter(String name, String... value) {
        super(name, value);
    }

    public Parameter(String name, List<String> value) {
        super(name, value);
    }
}
