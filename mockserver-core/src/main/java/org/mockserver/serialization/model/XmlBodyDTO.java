package org.mockserver.serialization.model;

import org.mockserver.model.Body;
import org.mockserver.model.XmlBody;

/**
 * @author jamesdbloom
 */
public class XmlBodyDTO extends BodyWithContentTypeDTO {

    private final String xml;

    public XmlBodyDTO(XmlBody xmlBody) {
        this(xmlBody, false);
    }

    public XmlBodyDTO(XmlBody xmlBody, Boolean not) {
        super(Body.Type.XML, not, xmlBody.getContentType());
        this.xml = xmlBody.getValue();
    }

    public String getXml() {
        return xml;
    }

    public XmlBody buildObject() {
        return new XmlBody(getXml(), getMediaType());
    }
}
