package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.NottableString;
import org.slf4j.event.Level;
import org.xml.sax.SAXException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;

import static org.mockserver.model.NottableString.string;
import static org.slf4j.event.Level.DEBUG;

/**
 * @author jamesdbloom
 */
public class XmlStringMatcher extends BodyMatcher<NottableString> {
    private static final String[] EXCLUDED_FIELDS = {"key", "mockServerLogger", "stringToXmlDocumentParser"};
    private final MockServerLogger mockServerLogger;
    private DiffBuilder diffBuilder;
    private NottableString matcher = string("THIS SHOULD NEVER MATCH");
    private final StringToXmlDocumentParser stringToXmlDocumentParser = new StringToXmlDocumentParser();

    XmlStringMatcher(MockServerLogger mockServerLogger, final String matcher) {
        this(mockServerLogger, string(matcher));
    }

    XmlStringMatcher(MockServerLogger mockServerLogger, final NottableString matcher) {
        this.mockServerLogger = mockServerLogger;
        try {
            this.matcher = normaliseXmlNottableString(matcher);
            this.diffBuilder = DiffBuilder.compare(Input.fromString(this.matcher.getValue()))
                .ignoreComments()
                .ignoreWhitespace()
                .checkForSimilar();
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("Error while creating xml string matcher for [" + matcher + "]" + e.getMessage())
                    .setThrowable(e)
            );
        }
    }

    public String normaliseXmlString(final String input) throws ParserConfigurationException, SAXException, IOException, TransformerException {
        return stringToXmlDocumentParser.normaliseXmlString(input, new StringToXmlDocumentParser.ErrorLogger() {
            @Override
            public void logError(final String matched, final Exception exception) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.EXCEPTION)
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("SAXParseException while parsing [" + input + "]")
                        .setThrowable(exception)
                );
            }
        });
    }

    public NottableString normaliseXmlNottableString(final NottableString input)
        throws IOException, SAXException, ParserConfigurationException, TransformerException {
        return string(normaliseXmlString(input.getValue()), input.isNot());
    }

    public boolean matches(String matched) {
        return matches(null, string(matched));
    }

    public boolean matches(final HttpRequest context, NottableString matched) {
        boolean result = false;

        if (diffBuilder != null) {
            try {
                Diff diff = diffBuilder.withTest(Input.fromString(normaliseXmlString(matched.getValue()))).build();
                result = !diff.hasDifferences();

                if (!result) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(LogEntry.LogMessageType.DEBUG)
                            .setLogLevel(DEBUG)
                            .setMessageFormat("Failed to perform xml schema match of {} with {} because {}")
                            .setArguments(matched, this.matcher, diff.toString())
                    );
                }

            } catch (Exception e) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.DEBUG)
                        .setLogLevel(DEBUG)
                        .setHttpRequest(context)
                        .setMessageFormat("Failed to perform xml schema match of {} with {} because {}")
                        .setArguments(matched, this.matcher, e.getMessage())
                );
            }
        }

        return matcher.isNot() == (not == result);
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
