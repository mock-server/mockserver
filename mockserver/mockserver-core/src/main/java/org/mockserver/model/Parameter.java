package org.mockserver.model;

import java.util.Arrays;
import java.util.Collection;

import static org.mockserver.model.NottableOptionalString.optional;
import static org.mockserver.model.NottableString.string;

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

    public Parameter(NottableString name, String... value) {
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

    public static Parameter param(NottableString name, String... value) {
        return new Parameter(name, value);
    }

    public static Parameter param(String name, Collection<String> value) {
        return new Parameter(name, value);
    }

    public static Parameter param(NottableString name, Collection<NottableString> value) {
        return new Parameter(name, value);
    }

    public static Parameter schemaParam(String name, String... values) {
        if (values.length == 0) {
            values = new String[]{".*"};
        }
        return new Parameter(string(name), Arrays.stream(values).map(NottableSchemaString::schemaString).toArray(NottableString[]::new));
    }

    public static Parameter schemaParam(NottableString name, String... values) {
        if (values.length == 0) {
            values = new String[]{".*"};
        }
        return new Parameter(name, Arrays.stream(values).map(NottableSchemaString::schemaString).toArray(NottableString[]::new));
    }

    public static Parameter optionalParam(String name, String... values) {
        if (values.length == 0) {
            values = new String[]{".*"};
        }
        return new Parameter(optional(name), Arrays.stream(values).map(NottableString::string).toArray(NottableString[]::new));
    }

    public Parameter withStyle(ParameterStyle style) {
        getName().withStyle(style);
        return this;
    }

}
