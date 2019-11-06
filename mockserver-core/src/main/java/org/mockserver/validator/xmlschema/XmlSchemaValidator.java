package org.mockserver.validator.xmlschema;

import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.validator.Validator;
import org.slf4j.event.Level;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.StringReader;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * @author jamesdbloom
 */
public class XmlSchemaValidator extends ObjectWithReflectiveEqualsHashCodeToString implements Validator<String> {

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(XmlSchemaValidator.class);
    private static final SchemaFactory schemaFactory = buildSchemaFactory();
    private final MockServerLogger mockServerLogger;
    private final Schema schema;

    public XmlSchemaValidator(MockServerLogger mockServerLogger, String schema) {
        this.mockServerLogger = mockServerLogger;
        try {
            if (schema.trim().endsWith(">") || isBlank(schema)) {
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

    private static SchemaFactory buildSchemaFactory() {
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "all");
            schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "all");
        } catch (Exception e) {
            MOCK_SERVER_LOGGER.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception configuring schema factory")
                    .setThrowable(e)
            );
        }
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
                    .setMessageFormat("Exception validating JSON")
                    .setThrowable(e)
            );
            return e.getClass().getSimpleName() + " - " + e.getMessage();
        }
        return errorMessage;
    }
}
