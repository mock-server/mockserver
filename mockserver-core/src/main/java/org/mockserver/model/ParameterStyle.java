package org.mockserver.model;

public enum ParameterStyle {
    // PATH
    SIMPLE("\\,"),
    SIMPLE_EXPLODED("\\,"),
    LABEL("\\,"),
    LABEL_EXPLODED("\\."),
    MATRIX("\\,"),
    MATRIX_EXPLODED(";<name>="),
    // QUERY
    FORM_EXPLODED(""),
    FORM("\\,"),
    SPACE_DELIMITED_EXPLODED(""),
    SPACE_DELIMITED("(%20)|\\s|\\+"),
    PIPE_DELIMITED_EXPLODED(""),
    PIPE_DELIMITED("\\|"),
    DEEP_OBJECT("");

    private final String regex;
    private final boolean exploded;

    ParameterStyle(String regex) {
        this.regex = regex;
        this.exploded = !regex.isEmpty();
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