package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.NottableString;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class XmlStringMatcher extends BodyMatcher<NottableString> {
    private static final String[] excludedFields = {"mockServerLogger", "stringToXmlDocumentParser"};
    private final MockServerLogger mockServerLogger;
    private NottableString matcher = string("THIS SHOULD NEVER MATCH");
    private StringToXmlDocumentParser stringToXmlDocumentParser = new StringToXmlDocumentParser();

    public XmlStringMatcher(MockServerLogger mockServerLogger, final String matcher) {
        this.mockServerLogger = mockServerLogger;
        try {
            this.matcher = string(normaliseXmlString(matcher));
        } catch (Exception e) {
            mockServerLogger.error("Error while creating xml string matcher for [" + matcher + "]" + e.getMessage(), e);
        }
    }

    public XmlStringMatcher(MockServerLogger mockServerLogger, final NottableString matcher) {
        this.mockServerLogger = mockServerLogger;
        try {
            this.matcher = normaliseXmlNottableString(matcher);
        } catch (Exception e) {
            mockServerLogger.error("Error while creating xml string matcher for [" + matcher + "]" + e.getMessage(), e);
        }
    }

    public String normaliseXmlString(final String input) throws ParserConfigurationException, SAXException, IOException, TransformerException {
        return stringToXmlDocumentParser.normaliseXmlString(input, new StringToXmlDocumentParser.ErrorLogger() {
            @Override
            public void logError(final String matched, final Exception exception) {
                mockServerLogger.error("SAXParseException while parsing [" + input + "]", exception);
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

    public boolean matches(HttpRequest context, NottableString matched) {
        boolean result = false;

        try {
            if (ExactStringMatcher.matches(matcher.getValue(), normaliseXmlString(matched.getValue()), false)) {
                result = true;
            }
        } catch (Exception e) {
            mockServerLogger.trace(context, "Error while matching xml string [" + matcher + "] against xml string [" + matched + "] assuming no match - " + e.getMessage());
        }

        if (!result) {
            mockServerLogger.trace(context, "Failed to match [{}] with [{}]", matched, this.matcher);
        }

        return matcher.isNot() != reverseResultIfNot(result);
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return excludedFields;
    }
}
