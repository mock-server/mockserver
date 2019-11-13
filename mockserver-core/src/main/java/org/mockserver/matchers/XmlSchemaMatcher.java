package org.mockserver.matchers;

import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.validator.xmlschema.XmlSchemaValidator;
import org.slf4j.event.Level;

import static org.slf4j.event.Level.DEBUG;
import static org.slf4j.event.Level.TRACE;

/**
 * See http://xml-schema.org/
 *
 * @author jamesdbloom
 */
public class XmlSchemaMatcher extends BodyMatcher<String> {
    private static final String[] EXCLUDED_FIELDS = {"key", "mockServerLogger", "xmlSchemaValidator"};
    private final MockServerLogger mockServerLogger;
    private String schema;
    private XmlSchemaValidator xmlSchemaValidator;

    public XmlSchemaMatcher(MockServerLogger mockServerLogger, String schema) {
        this.mockServerLogger = mockServerLogger;
        this.schema = schema;
        xmlSchemaValidator = new XmlSchemaValidator(mockServerLogger, schema);
    }

    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }

    public boolean matches(final HttpRequest context, String matched) {
        boolean result = false;

        try {
            String validation = xmlSchemaValidator.isValid(matched);

            result = validation.isEmpty();

            if (!result) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.DEBUG)
                        .setLogLevel(DEBUG)
                        .setHttpRequest(context)
                        .setMessageFormat("Failed to perform xml schema match of {} with {} because {}")
                        .setArguments(matched, this.schema, validation)
                );
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.DEBUG)
                    .setLogLevel(DEBUG)
                    .setHttpRequest(context)
                    .setMessageFormat("Failed to perform xml schema match of {} with {} because {}")
                    .setArguments(matched, this.schema, e.getMessage())
            );
        }

        return not != result;
    }

}
