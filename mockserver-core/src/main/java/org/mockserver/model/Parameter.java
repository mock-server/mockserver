package org.mockserver.model;

import java.util.Arrays;
import java.util.Collection;

import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.Style.DEEP_OBJECT;

/**
 * @author jamesdbloom
 */
public class Parameter extends KeyToMultiValue {

    private Style style;

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

    public static Parameter schemaParam(String name, String... values) {
        return new Parameter(string(name), Arrays.stream(values).map(NottableSchemaString::schemaString).toArray(NottableString[]::new));
    }

    public Style getStyle() {
        return style;
    }

    public Parameter withStyle(Style style) {
        if (style.equals(DEEP_OBJECT)) {
            throw new IllegalArgumentException("deep object style is not supported");
        }
        this.style = style;
        return this;
    }

    public enum Style {
        MATRIX(","),
        MATRIX_EXPLODE(""),
        LABEL(","),
        LABEL_EXPLODE(""),
        SIMPLE(""),
        SIMPLE_EXPLODE(""),
        FORM(","),
        FORM_EXPLODE(""),
        SPACE_DELIMITED("(%20)|\\s|\\+"),
        SPACE_DELIMITED_EXPLODE(""),
        PIPE_DELIMITED("\\|"),
        PIPE_DELIMITED_EXPLODE(""),
        DEEP_OBJECT("");

        private final String regex;
        private final boolean exploded;

        Style(String regex) {
            this.regex = regex;
            this.exploded = regex.isEmpty();
        }

        public String getRegex() {
            return regex;
        }

        public boolean isExploded() {
            return exploded;
        }

        @Override
        public String toString() {
            return name() + "(" + regex + "," + Boolean.toString(exploded) + ")";
        }
    }
}
