package org.mockserver.model;

import java.util.Objects;

public class PathModifier extends ObjectWithJsonToString {

    private int hashCode;
    private String regex;
    private String substitution;

    public String getRegex() {
        return regex;
    }

    /**
     * <p>
     * The regex value to use to modify matching substrings, if multiple matches are found they will all be modified with the substitution
     * for full details of supported regex syntax see: http://docs.oracle.com/javase/7/docs/technotes/guides/security/SunProviders.html
     * </p>
     * <p>
     * If a null or empty substitution string is provided the regex pattern will be used to remove any substring matching the regex
     * </p>
     * <p>
     * For example:
     * </p>
     * <pre>
     * regex: ^/(.+)/(.+)$
     * substitution: /prefix/$1/infix/$2/postfix
     * then: /some/path &#61;&gt; /prefix/some/infix/path/postfix
     * or: /some/longer/path &#61;&gt; /prefix/some/infix/longer/path/postfix
     * </pre>
     *
     * @param regex regex value to match on
     */
    public PathModifier withRegex(String regex) {
        this.regex = regex;
        this.hashCode = 0;
        return this;
    }

    public String getSubstitution() {
        return substitution;
    }

    /**
     * <p>
     * The pattern to substitute for the matched regex, matching groups are supported using $ followed by the group number for example $1
     * </p>
     * <p>
     * If a null or empty substitution string is provided the regex pattern will be used to remove any substring matching the regex
     * </p>
     *
     * @param substitution the value to substitute for the regex
     */
    public PathModifier withSubstitution(String substitution) {
        this.substitution = substitution;
        this.hashCode = 0;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        PathModifier that = (PathModifier) o;
        return Objects.equals(regex, that.regex) &&
            Objects.equals(substitution, that.substitution);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), regex, substitution);
        }
        return hashCode;
    }

}
