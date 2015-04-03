package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class XPathBody extends Body {

    private final String xpath;

    public XPathBody(String xpath) {
        super(Type.XPATH);
        this.xpath = xpath;
    }

    public String getValue() {
        return xpath;
    }

    public static XPathBody xpath(String xpath) {
        return new XPathBody(xpath);
    }

}
