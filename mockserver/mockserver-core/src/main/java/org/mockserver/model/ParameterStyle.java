package org.mockserver.model;

public enum ParameterStyle {
    // PATH
    SIMPLE("\\,", false),
    SIMPLE_EXPLODED("\\,", true),
    LABEL("\\,", false),
    LABEL_EXPLODED("\\.", true),
    MATRIX("\\,", false),
    MATRIX_EXPLODED(";<name>=", true),
    // QUERY
    FORM_EXPLODED("", true),
    FORM("\\,", false),
    SPACE_DELIMITED_EXPLODED("", true),
    SPACE_DELIMITED("(%20)|\\s|\\+", false),
    PIPE_DELIMITED_EXPLODED("", true),
    PIPE_DELIMITED("\\|", false),
    DEEP_OBJECT("", true);

    private final String regex;
    private final boolean exploded;
    private final boolean explodedObjectStyle;

    ParameterStyle(String regex, boolean explodedObjectStyle) {
        this.regex = regex;
        this.exploded = !regex.isEmpty();
        this.explodedObjectStyle = explodedObjectStyle;
    }

    public String getRegex() {
        return regex;
    }

    public boolean isExploded() {
        return exploded;
    }

    public boolean isExplodedObjectStyle() {
        return explodedObjectStyle;
    }

    @Override
    public String toString() {
        return name() + "(" + regex + "," + Boolean.toString(exploded) + ")";
    }
}