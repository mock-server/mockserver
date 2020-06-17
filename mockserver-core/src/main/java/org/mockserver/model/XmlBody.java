package org.mockserver.model;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Objects;

import static org.mockserver.model.MediaType.DEFAULT_HTTP_CHARACTER_SET;

/**
 * @author jamesdbloom
 */
public class XmlBody extends BodyWithContentType<String> {
    private int hashCode;
    // setting default to UTF8 as per https://tools.ietf.org/html/rfc3470#section-5.1
    public static final MediaType DEFAULT_CONTENT_TYPE = MediaType.APPLICATION_XML_UTF_8;
    private final String xml;
    private final byte[] rawBytes;

    public XmlBody(String xml) {
        this(xml, DEFAULT_CONTENT_TYPE);
    }

    public XmlBody(String xml, Charset charset) {
        this(xml, null, (charset != null ? DEFAULT_CONTENT_TYPE.withCharset(charset) : null));
    }

    public XmlBody(String xml, MediaType contentType) {
        this(xml, null, contentType);
    }

    public XmlBody(String xml, byte[] rawBytes, MediaType contentType) {
        super(Type.XML, contentType);
        this.xml = xml;

        if (rawBytes == null && xml != null) {
            this.rawBytes = xml.getBytes(determineCharacterSet(contentType, DEFAULT_HTTP_CHARACTER_SET));
        } else {
            this.rawBytes = rawBytes;
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
        return rawBytes;
    }

    @Override
    public String toString() {
        return xml;
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
        XmlBody xmlBody = (XmlBody) o;
        return Objects.equals(xml, xmlBody.xml) &&
            Arrays.equals(rawBytes, xmlBody.rawBytes);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            int result = Objects.hash(super.hashCode(), xml);
            hashCode = 31 * result + Arrays.hashCode(rawBytes);
        }
        return hashCode;
    }
}
