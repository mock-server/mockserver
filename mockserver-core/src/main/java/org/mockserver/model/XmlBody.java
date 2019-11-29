package org.mockserver.model;

import java.nio.charset.Charset;

import static org.mockserver.mappers.ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET;

/**
 * @author jamesdbloom
 */
public class XmlBody extends BodyWithContentType<String> {

    public static final MediaType DEFAULT_CONTENT_TYPE = MediaType.create("application", "xml");
    private final String xml;
    private final byte[] rawBinaryData;

    public XmlBody(String xml) {
        this(xml, DEFAULT_CONTENT_TYPE);
    }

    public XmlBody(String xml, Charset charset) {
        this(xml, (charset != null ? MediaType.create("application", "xml").withCharset(charset) : null));
    }

    public XmlBody(String xml, MediaType contentType) {
        super(Type.XML, contentType);
        this.xml = xml;

        if (xml != null) {
            this.rawBinaryData = xml.getBytes(determineCharacterSet(contentType, DEFAULT_HTTP_CHARACTER_SET));
        } else {
            this.rawBinaryData = new byte[0];
        }
    }

    public static XmlBody xml(String xml) {
        return new XmlBody(xml);
    }

    public static XmlBody xml(String xml, Charset charset) {
        return new XmlBody(xml, charset);
    }

    public static XmlBody xml(String xml, MediaType contentType) {
        return new XmlBody(xml, contentType);
    }

    public String getValue() {
        return xml;
    }

    public byte[] getRawBytes() {
        return rawBinaryData;
    }

    @Override
    public String toString() {
        return xml;
    }
}
