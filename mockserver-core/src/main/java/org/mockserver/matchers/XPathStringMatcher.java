package org.mockserver.matchers;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;

/**
 * @author jamesdbloom
 */
public class XPathStringMatcher extends BodyMatcher implements Matcher<String> {
    private static Logger logger = LoggerFactory.getLogger(XPathStringMatcher.class);
    private final String matcher;
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
        if (xpathExpression == null) {
            logger.warn("Attempting match against null XPath Expression for [" + matched + "]" + new RuntimeException("Attempting match against null XPath Expression for [" + matched + "]"));
        } else if (matched != null) {
            // match as xpath - matcher -> matched
            try {
                DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
                documentBuilder.setErrorHandler(new ErrorHandler() {
                    @Override
                    public void warning(SAXParseException exception) throws SAXException {
                        logger.warn("SAXParseException while performing match between [" + matcher + "] and [" + matched + "]", exception);
                    }

                    @Override
                    public void error(SAXParseException exception) throws SAXException {
                        logger.warn("SAXParseException while performing match between [" + matcher + "] and [" + matched + "]", exception);
                    }

                    @Override
                    public void fatalError(SAXParseException exception) throws SAXException {
                        logger.warn("SAXParseException while performing match between [" + matcher + "] and [" + matched + "]", exception);
                    }
                });
                return (Boolean) xpathExpression.evaluate(documentBuilder.parse(new InputSource(new StringReader(matched))), XPathConstants.BOOLEAN);
            } catch (Exception e) {
                logger.trace("Error while matching xpath [" + matcher + "] against string [" + matched + "] assuming no match - " + e.getMessage());
            }
        }

        logger.trace("Failed to match [{}] with [{}]", matched, this.matcher);
        return false;
    }

    @Override
    public String[] fieldsExcludedFromEqualsAndHashCode() {
        return new String[]{"logger", "xpathExpression"};
    }
}
