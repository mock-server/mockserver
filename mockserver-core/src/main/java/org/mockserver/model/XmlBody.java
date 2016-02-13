package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class XmlBody extends Body {

    private final String xml;

    public XmlBody(String xml) {
        super(Type.XML);
        this.xml = xml;
    }

    public static XmlBody xml(String xml) {
        return new XmlBody(xml);
    }

    public String getValue() {
        return xml;
    }

}
