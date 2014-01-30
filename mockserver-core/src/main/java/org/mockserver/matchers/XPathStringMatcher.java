package org.mockserver.matchers;

import com.sun.org.apache.xerces.internal.impl.Constants;
import com.sun.org.apache.xerces.internal.impl.XMLErrorReporter;
import com.sun.org.apache.xerces.internal.util.DefaultErrorHandler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

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

    public boolean matches(String matched) {
        boolean result = false;

        if (xpathExpression == null) {
            logger.warn("Attempting match against null XPath Expression for [" + matched + "]" + new RuntimeException("Attempting match against null XPath Expression for [" + matched + "]"));
        } else if (matched != null) {
            // match as xpath - matcher -> matched
            try {
                // overly complex way to build xml required to prevent xerces from outputting errors about xml format to System.err
                DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                documentBuilderFactory.setAttribute(Constants.XERCES_PROPERTY_PREFIX + Constants.ERROR_REPORTER_PROPERTY, new XMLErrorReporter() {{
                    setProperty(Constants.XERCES_PROPERTY_PREFIX + Constants.ERROR_HANDLER_PROPERTY, new DefaultErrorHandler(new PrintWriter(new StringWriter())));
                }});
                return (Boolean) xpathExpression.evaluate(documentBuilderFactory.newDocumentBuilder().parse(new InputSource(new StringReader(matched))), XPathConstants.BOOLEAN);
            } catch (Exception e) {
                logger.trace("Error while matching xpath [" + matcher + "] against string [" + matched + "] assuming no match - " + e.getMessage());
            }
        }

        if (!result) {
            logger.trace("Failed to match [{}] with [{}]", matched, this.matcher);
        }
        return result;
    }

    @Override
    public String[] fieldsExcludedFromEqualsAndHashCode() {
        return new String[]{"logger", "xpathExpression"};
    }
}
