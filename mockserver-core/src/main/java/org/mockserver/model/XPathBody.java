package org.mockserver.model;

import java.util.Objects;

/**
 * @author jamesdbloom
 */
public class XPathBody extends Body<String> {
    private int hashCode;
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
        XPathBody xPathBody = (XPathBody) o;
        return Objects.equals(xpath, xPathBody.xpath);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), xpath);
        }
        return hashCode;
    }
}
