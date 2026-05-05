package org.mockserver.model;

import org.mockserver.file.FileReader;

import java.util.Objects;

/**
 * @author jamesdbloom
 */
public class XmlSchemaBody extends Body<String> {
    private int hashCode;
    private final String xmlSchema;

    public XmlSchemaBody(String xmlSchema) {
        super(Type.XML_SCHEMA);
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
        XmlSchemaBody that = (XmlSchemaBody) o;
        return Objects.equals(xmlSchema, that.xmlSchema);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), xmlSchema);
        }
        return hashCode;
    }
}
