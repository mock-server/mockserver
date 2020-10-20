package org.mockserver.serialization.model;

import org.mockserver.model.Body;
import org.mockserver.model.XmlSchemaBody;

/**
 * @author jamesdbloom
 */
public class XmlSchemaBodyDTO extends BodyDTO {

    private final String xmlSchema;

    public XmlSchemaBodyDTO(XmlSchemaBody xmlSchemaBody) {
        this(xmlSchemaBody, null);
    }

    public XmlSchemaBodyDTO(XmlSchemaBody xmlSchemaBody, Boolean not) {
        super(Body.Type.XML_SCHEMA, not);
        this.xmlSchema = xmlSchemaBody.getValue();
        withOptional(xmlSchemaBody.getOptional());
    }

    public String getXml() {
        return xmlSchema;
    }

    public XmlSchemaBody buildObject() {
        return (XmlSchemaBody) new XmlSchemaBody(getXml()).withOptional(getOptional());
    }
}
