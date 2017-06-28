package org.mockserver.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import joptsimple.internal.Strings;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.file.FileReader;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import sun.nio.cs.StandardCharsets;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.CharEncoding.UTF_8;

/**
 * @author jamesdbloom
 */
public class XmlSchemaValidator extends ObjectWithReflectiveEqualsHashCodeToString implements Validator<String> {

    private final Schema schema;
    public Logger logger = LoggerFactory.getLogger(this.getClass());
    private final static SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

    public XmlSchemaValidator(String schema) {
        try {
            if (schema.trim().endsWith(">") || Strings.isNullOrEmpty(schema)) {
                this.schema = schemaFactory.newSchema(new StreamSource(new StringReader(schema)));
            } else if (schema.trim().endsWith(".xsd")) {
                this.schema = schemaFactory.newSchema(new StreamSource(FileReader.openReaderToFileFromClassPathOrPath(schema)));
            } else {
                throw new IllegalArgumentException("Schema must either be a path reference to a *.xsd file or an xml string");
            }
        } catch (SAXException e) {
            throw new IllegalArgumentException("Schema is not valid", e);
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Schema file not found", e);
        }
    }

    @Override
    public String isValid(String xml) {
        String errorMessage = "";
        try {
            try {
                schema.newValidator().validate(new StreamSource(new ByteArrayInputStream(xml.getBytes(UTF_8))));
            } catch (SAXException e) {
                errorMessage = e.getMessage();
            }
        } catch (Exception e) {
            logger.info("Exception validating JSON", e);
            return e.getClass().getSimpleName() + " - " + e.getMessage();
        }
        return errorMessage;
    }
}
