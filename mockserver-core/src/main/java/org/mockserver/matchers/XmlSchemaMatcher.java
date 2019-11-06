package org.mockserver.matchers;

import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.validator.xmlschema.XmlSchemaValidator;

import static org.slf4j.event.Level.TRACE;

/**
 * See http://xml-schema.org/
 *
 * @author jamesdbloom
 */
public class XmlSchemaMatcher extends BodyMatcher<String> {
    private final MockServerLogger mockServerLogger;
    private String schema;
    private XmlSchemaValidator xmlSchemaValidator;

    public XmlSchemaMatcher(MockServerLogger mockServerLogger, String schema) {
        this.mockServerLogger = mockServerLogger;
        this.schema = schema;
        xmlSchemaValidator = new XmlSchemaValidator(mockServerLogger, schema);
    }

    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return new String[]{"logger", "xmlSchemaValidator"};
    }

    public boolean matches(final HttpRequest context, String matched) {
        boolean result = false;

        try {
            String validation = xmlSchemaValidator.isValid(matched);

            result = validation.isEmpty();

            if (!result) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.TRACE)
                        .setLogLevel(TRACE)
                        .setHttpRequest(context)
                        .setMessageFormat("Failed to match [{}] with schema [{}] because [{}]")
                        .setArguments(matched, this.schema, validation)
                );
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.TRACE)
                    .setLogLevel(TRACE)
                    .setHttpRequest(context)
                    .setMessageFormat("Failed to match [{}] with schema [{}] because [{}]")
                    .setArguments(matched, this.schema, e.getMessage())
            );
        }

        return not != result;
    }

}
