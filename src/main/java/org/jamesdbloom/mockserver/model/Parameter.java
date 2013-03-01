package org.jamesdbloom.mockserver.model;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class Parameter extends KeyToMultiValue<String, String> {

    public Parameter(String name, String... value) {
        super(name, value);
    }

    public Parameter(String name, List<String> value) {
        super(name, value);
    }
}
