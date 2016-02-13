package org.mockserver.client.serialization.model;

import org.mockserver.model.Body;
import org.mockserver.model.XmlBody;

/**
 * @author jamesdbloom
 */
public class XmlBodyDTO extends BodyDTO {

    private String xml;

    public XmlBodyDTO(XmlBody xmlBody) {
        this(xmlBody, false);
    }

    public XmlBodyDTO(XmlBody xmlBody, Boolean not) {
        super(Body.Type.XML, not);
        this.xml = xmlBody.getValue();
    }

    protected XmlBodyDTO() {
    }

    public String getXml() {
        return xml;
    }

    public XmlBody buildObject() {
        return new XmlBody(getXml());
    }
}
