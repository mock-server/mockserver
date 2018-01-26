package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * @author jamesdbloom
 */
public class XPathStringMatcher extends BodyMatcher<String> {
    private static final String[] excludedFields = {"mockServerLogger", "xpathExpression"};
    private final MockServerLogger mockServerLogger;
    private final String matcher;
    private final StringToXmlDocumentParser stringToXmlDocumentParser = new StringToXmlDocumentParser();
    private XPathExpression xpathExpression = null;

    public XPathStringMatcher(MockServerLogger mockServerLogger, String matcher) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;
        if (StringUtils.isNotEmpty(matcher)) {
            try {
                xpathExpression = XPathFactory.newInstance().newXPath().compile(matcher);
            } catch (XPathExpressionException e) {
                mockServerLogger.trace("Error while creating xpath expression for [" + matcher + "] assuming matcher not xpath - " + e.getMessage(), e);
            }
        }
    }

    public boolean matches(final HttpRequest context, final String matched) {
        boolean result = false;

        if (xpathExpression == null) {
            mockServerLogger.debug(context, "Attempting match against null XPath Expression for [" + matched + "]" + new RuntimeException("Attempting match against null XPath Expression for [" + matched + "]"));
        } else if (matcher.equals(matched)) {
            result = true;
        } else if (matched != null) {
            // match as xpath - matcher -> matched
            try {
                result = (Boolean) xpathExpression.evaluate(stringToXmlDocumentParser.buildDocument(matched, new StringToXmlDocumentParser.ErrorLogger() {
                    @Override
                    public void logError(final String matched, final Exception exception) {
                        mockServerLogger.debug(context, "SAXParseException while performing match between [" + matcher + "] and [" + matched + "]", exception);
                    }
                }), XPathConstants.BOOLEAN);
            } catch (Exception e) {
                mockServerLogger.trace(context, "Error while matching xpath [" + matcher + "] against string [" + matched + "] assuming no match - " + e.getMessage());
            }
        }

        if (!result) {
            mockServerLogger.trace("Failed to match [{}] with [{}]", matched, this.matcher);
        }

        return not != result;
    }

    @Override
    @JsonIgnore
    public String[] fieldsExcludedFromEqualsAndHashCode() {
        return excludedFields;
    }
}
