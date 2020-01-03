package org.mockserver.validator.xmlschema;

import org.mockserver.file.FileReader;
import org.mockserver.formatting.StringFormatter;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.validator.Validator;
import org.slf4j.event.Level;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * @author jamesdbloom
 */
public class XmlSchemaValidator extends ObjectWithReflectiveEqualsHashCodeToString implements Validator<String> {

    private static SchemaFactory schemaFactory;
    private final MockServerLogger mockServerLogger;
    private final Schema schema;

    public XmlSchemaValidator(MockServerLogger mockServerLogger, String schema) {
        this.mockServerLogger = mockServerLogger;
        try {
            if (schemaFactory == null) {
                schemaFactory = buildSchemaFactory();
            }
            if (schema.trim().endsWith(">") || isBlank(schema)) {
                this.schema = schemaFactory.newSchema(new StreamSource(new StringReader(schema)));
            } else if (schema.trim().endsWith(".xsd")) {
                this.schema = schemaFactory.newSchema(new StreamSource(FileReader.openReaderToFileFromClassPathOrPath(schema)));
            } else {
                throw new IllegalArgumentException("Schema must either be a path reference to a *.xsd file or an xml string");
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception parsing schema{}")
                    .setArguments(schema)
                    .setThrowable(e)
            );
            throw new RuntimeException(StringFormatter.formatLogMessage("exception parsing schema{}", schema), e);
        }
    }

    private SchemaFactory buildSchemaFactory() throws SAXNotRecognizedException, SAXNotSupportedException {
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "all");
        schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "all");
        return schemaFactory;
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
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception validating JSON")
                    .setThrowable(e)
            );
            return e.getClass().getSimpleName() + " - " + e.getMessage();
        }
        return errorMessage;
    }
}
