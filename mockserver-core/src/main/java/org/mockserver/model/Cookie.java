package org.mockserver.model;

import static org.mockserver.model.NottableOptionalString.optionalString;
import static org.mockserver.model.NottableSchemaString.schemaString;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class Cookie extends KeyAndValue {

    public Cookie(String name, String value) {
        super(name, value);
    }

    public Cookie(NottableString name, NottableString value) {
        super(name, value);
    }

    public static Cookie cookie(String name, String value) {
        return new Cookie(name, value);
    }

    public static Cookie cookie(NottableString name, NottableString value) {
        return new Cookie(name, value);
    }

    public static Cookie schemaCookie(String name, String value) {
        return new Cookie(string(name), schemaString(value));
    }

    public static Cookie optionalCookie(String name, String value) {
        return new Cookie(optionalString(name), string(value));
    }
}
