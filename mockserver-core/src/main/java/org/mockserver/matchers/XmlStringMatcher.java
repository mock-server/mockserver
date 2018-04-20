package org.mockserver.matchers;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.mockserver.model.NottableString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

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

    public String normaliseXmlString(final String input) throws ParserConfigurationException, SAXException, IOException, TransformerException {
        return stringToXmlDocumentParser.normaliseXmlString(input, new StringToXmlDocumentParser.ErrorLogger() {
            @Override
            public void logError(final String matched, final Exception exception) {
                logger.debug("SAXParseException while parsing [" + input + "]", exception);
            }
        });
    }

    public NottableString normaliseXmlNottableString(final NottableString input)
        throws IOException, SAXException, ParserConfigurationException, TransformerException {
        return string(normaliseXmlString(input.getValue()), input.getNot());
    }

    public boolean matches(String matched) {
        return matches(string(matched));
    }

    public boolean matches(NottableString matched) {
        boolean result = false;

        Diff xmlDiff = null;
        try {

            // previously we used an ExactStringMatcher, but now use XMLDiff library instead.
            final String actual = normaliseXmlString(matched.getValue());
            xmlDiff = differenceOf(actual);
            result = !xmlDiff.hasDifferences();

        } catch (Exception e) {
            logger.trace("Error while matching xml string [" + matcher + "] against xml string [" + matched + "] assuming no match - " + e.getMessage());
        }

        if (!result) {
            logger.trace("Failed to match [{}] with [{}]", matched, this.matcher);
        }

        final boolean matches = matcher.isNot() != reverseResultIfNot(result);
        if (!matches && xmlDiff != null) {
            logger.info(xmlDiff.toString());
        }
        return matches;
    }

    private Diff differenceOf(final String actual) {
        final String expected = matcher.getValue();
        return DiffBuilder.compare(Input.fromString(expected))
                .withTest(Input.fromString(actual))
                .ignoreComments()
                .ignoreWhitespace()
                .checkForSimilar()
                .build();
    }

    public String explainDifference(final String actual) {
        final String s = differenceOf(actual).toString();
        return s;
    }
}
