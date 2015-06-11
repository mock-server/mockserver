package org.mockserver.model;

import java.util.Collection;

/**
 * @author jamesdbloom
 */
public class Parameter extends KeyToMultiValue {

    public Parameter(String name, String... value) {
        super(name, value);
    }

    public Parameter(NottableString name, NottableString... value) {
        super(name, value);
    }

    public Parameter(String name, Collection<String> value) {
        super(name, value);
    }

    public Parameter(NottableString name, Collection<NottableString> value) {
        super(name, value);
    }

    public static Parameter param(String name, String... value) {
        return new Parameter(name, value);
    }

    public static Parameter param(NottableString name, NottableString... value) {
        return new Parameter(name, value);
    }

    public static Parameter param(String name, Collection<String> value) {
        return new Parameter(name, value);
    }

    public static Parameter param(NottableString name, Collection<NottableString> value) {
        return new Parameter(name, value);
    }
}
