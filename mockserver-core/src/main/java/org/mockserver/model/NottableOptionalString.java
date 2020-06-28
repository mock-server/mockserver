package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class NottableOptionalString extends NottableString {

    public static final char OPTIONAL_CHAR = '?';

    public static NottableOptionalString optionalString(String value, Boolean not) {
        return new NottableOptionalString(value, not);
    }

    public static NottableOptionalString optionalString(String value) {
        return new NottableOptionalString(value);
    }

    public static NottableOptionalString notOptional(String value) {
        return new NottableOptionalString(value, Boolean.TRUE);
    }

    private NottableOptionalString(String schema, Boolean not) {
        super(schema, not);
    }

    private NottableOptionalString(String schema) {
        super(schema);
    }

    @Override
    public boolean isOptional() {
        return true;
    }

}
