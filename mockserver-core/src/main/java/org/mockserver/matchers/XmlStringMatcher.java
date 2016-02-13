package org.mockserver.matchers;

import org.mockserver.model.NottableString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class XmlStringMatcher extends BodyMatcher<NottableString> {
    private static Logger logger = LoggerFactory.getLogger(XmlStringMatcher.class);
    private NottableString matcher = string("THIS SHOULD NEVER MATCH");
    private StringToXmlDocumentParser stringToXmlDocumentParser = new StringToXmlDocumentParser();

    public XmlStringMatcher(final String matcher) {
        try {
            this.matcher = string(normaliseXmlString(matcher));
        } catch (Exception e) {
            logger.error("Error while creating xml string matcher for [" + matcher + "]" + e.getMessage(), e);
        }
    }

    public XmlStringMatcher(final NottableString matcher) {
        try {
            this.matcher = normaliseXmlNottableString(matcher);
        } catch (Exception e) {
            logger.error("Error while creating xml string matcher for [" + matcher + "]" + e.getMessage(), e);
        }
    }

    public String normaliseXmlString(final String input) throws ParserConfigurationException, SAXException, IOException {
        return stringToXmlDocumentParser.normaliseXmlString(input, new StringToXmlDocumentParser.ErrorLogger() {
            @Override
            public void logError(final String matched, final Exception exception) {
                logger.debug("SAXParseException while parsing [" + input + "]", exception);
            }
        });
    }

    public NottableString normaliseXmlNottableString(final NottableString input) throws IOException, SAXException, ParserConfigurationException {
        return string(normaliseXmlString(input.getValue()), input.getNot());
    }

    public boolean matches(String matched) {
        return matches(string(matched));
    }

    public boolean matches(NottableString matched) {
        boolean result = false;

        try {
            if (ExactStringMatcher.matches(matcher.getValue(), normaliseXmlString(matched.getValue()), false)) {
                result = true;
            }
        } catch (Exception e) {
            logger.trace("Error while matching xml string [" + matcher + "] against xml string [" + matched + "] assuming no match - " + e.getMessage());
        }

        if (!result) {
            logger.trace("Failed to match [{}] with [{}]", matched, this.matcher);
        }

        return matcher.isNot() != reverseResultIfNot(result);
    }
}
