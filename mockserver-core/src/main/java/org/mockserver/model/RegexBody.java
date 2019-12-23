package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class RegexBody extends Body {

    private final String regex;

    public RegexBody(String regex) {
        super(Type.REGEX);
        this.regex = regex;
    }

    public String getValue() {
        return regex;
    }

    public static RegexBody regex(String regex) {
        return new RegexBody(regex);
    }

}
