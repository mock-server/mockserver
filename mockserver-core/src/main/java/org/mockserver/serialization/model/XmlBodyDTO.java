package org.mockserver.serialization.model;

import org.mockserver.model.Body;
import org.mockserver.model.XmlBody;

/**
 * @author jamesdbloom
 */
public class XmlBodyDTO extends BodyWithContentTypeDTO {

    private final String xml;
    private final byte[] rawBytes;

    public XmlBodyDTO(XmlBody xmlBody) {
        this(xmlBody, null);
    }

    public XmlBodyDTO(XmlBody xmlBody, Boolean not) {
        super(Body.Type.XML, not, xmlBody);
        xml = xmlBody.getValue();
        rawBytes = xmlBody.getRawBytes();
    }

    public String getXml() {
        return xml;
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    public XmlBody buildObject() {
        return (XmlBody) new XmlBody(getXml(), getRawBytes(), getMediaType()).withOptional(getOptional());
    }
}
