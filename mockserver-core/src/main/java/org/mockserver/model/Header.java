package org.mockserver.model;

import java.util.Arrays;
import java.util.Collection;

import static org.mockserver.model.NottableOptionalString.optional;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class Header extends KeyToMultiValue {

    public Header(String name, String... value) {
        super(name, value);
    }

    public Header(NottableString name, NottableString... value) {
        super(name, value);
    }

    public Header(NottableString name, String... value) {
        super(name, value);
    }

    public Header(String name, Collection<String> value) {
        super(name, value);
    }

    public Header(NottableString name, Collection<NottableString> value) {
        super(name, value);
    }

    public static Header header(String name, int value) {
        return new Header(name, String.valueOf(value));
    }

    public static Header header(String name, String... value) {
        return new Header(name, value);
    }

    public static Header header(NottableString name, NottableString... value) {
        return new Header(name, value);
    }

    public static Header header(String name, Collection<String> value) {
        return new Header(name, value);
    }

    public static Header header(NottableString name, Collection<NottableString> value) {
        return new Header(name, value);
    }

    public static Header schemaHeader(String name, String... values) {
        return new Header(string(name), Arrays.stream(values).map(NottableSchemaString::schemaString).toArray(NottableString[]::new));
    }

    public static Header optionalHeader(String name, String... values) {
        return new Header(optional(name), Arrays.stream(values).map(NottableString::string).toArray(NottableString[]::new));
    }
}
