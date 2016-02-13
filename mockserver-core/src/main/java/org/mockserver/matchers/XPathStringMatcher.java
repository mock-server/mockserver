package org.mockserver.matchers;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * @author jamesdbloom
 */
public class XPathStringMatcher extends BodyMatcher<String> {
    private static Logger logger = LoggerFactory.getLogger(XPathStringMatcher.class);
    private final String matcher;
    private final StringToXmlDocumentParser stringToXmlDocumentParser = new StringToXmlDocumentParser();
    private XPathExpression xpathExpression = null;

    public XPathStringMatcher(String matcher) {
        this.matcher = matcher;
        if (StringUtils.isNotEmpty(matcher)) {
            try {
                xpathExpression = XPathFactory.newInstance().newXPath().compile(matcher);
            } catch (XPathExpressionException e) {
                logger.trace("Error while creating xpath expression for [" + matcher + "] assuming matcher not xpath - " + e.getMessage(), e);
            }
        }
    }

    public boolean matches(final String matched) {
        boolean result = false;

        if (xpathExpression == null) {
            logger.warn("Attempting match against null XPath Expression for [" + matched + "]" + new RuntimeException("Attempting match against null XPath Expression for [" + matched + "]"));
        } else if (matcher.equals(matched)) {
            result = true;
        } else if (matched != null) {
            // match as xpath - matcher -> matched
            try {
                result = (Boolean) xpathExpression.evaluate(stringToXmlDocumentParser.buildDocument(matched, new StringToXmlDocumentParser.ErrorLogger() {
                    @Override
                    public void logError(final String matched, final Exception exception) {
                        logger.debug("SAXParseException while performing match between [" + matcher + "] and [" + matched + "]", exception);
                    }
                }), XPathConstants.BOOLEAN);
            } catch (Exception e) {
                logger.trace("Error while matching xpath [" + matcher + "] against string [" + matched + "] assuming no match - " + e.getMessage());
            }
        }

        if (!result) {
            logger.trace("Failed to match [{}] with [{}]", matched, this.matcher);
        }

        return reverseResultIfNot(result);
    }

    @Override
    public String[] fieldsExcludedFromEqualsAndHashCode() {
        return new String[]{"logger", "xpathExpression"};
    }
}
