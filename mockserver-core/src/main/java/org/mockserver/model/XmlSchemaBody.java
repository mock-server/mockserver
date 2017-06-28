package org.mockserver.model;

import com.google.common.net.MediaType;
import org.mockserver.file.FileReader;

/**
 * @author jamesdbloom
 */
public class XmlSchemaBody extends Body {

    public static final MediaType DEFAULT_CONTENT_TYPE = MediaType.create("application", "xml");
    private final String xmlSchema;

    public XmlSchemaBody(String xmlSchema) {
        super(Type.XML_SCHEMA, DEFAULT_CONTENT_TYPE);
        this.xmlSchema = xmlSchema;
    }

    public static XmlSchemaBody xmlSchema(String xmlSchema) {
        return new XmlSchemaBody(xmlSchema);
    }

    public static XmlSchemaBody xmlSchemaFromResource(String xmlSchemaPath) {
        return new XmlSchemaBody(FileReader.readFileFromClassPathOrPath(xmlSchemaPath));
    }

    public String getValue() {
        return xmlSchema;
    }
}
