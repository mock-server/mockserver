package org.mockserver.matchers;

import com.google.common.base.Strings;
import com.sun.org.apache.xerces.internal.impl.Constants;
import com.sun.org.apache.xerces.internal.impl.XMLErrorReporter;
import com.sun.org.apache.xerces.internal.util.DefaultErrorHandler;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.model.EqualsHashCodeToString;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.regex.PatternSyntaxException;

/**
 * @author jamesdbloom
 */
public class StringMatcher extends EqualsHashCodeToString implements Matcher<String> {

    private final String matcher;
    XPathExpression xpathExpression = null;

    public StringMatcher(String matcher) {
        this.matcher = matcher;
        if (StringUtils.isNotEmpty(matcher)) {
            try {
                xpathExpression = XPathFactory.newInstance().newXPath().compile(matcher);
            } catch (XPathExpressionException e) {
                logger.trace("Error while creating xpath expression for [" + matcher + "] assuming matcher not xpath - " + e.getMessage());
            }
        }
    }

    public boolean matches(String matched) {
        boolean result = false;

        if (Strings.isNullOrEmpty(this.matcher)) {
            result = true;
        } else if (matched != null) {
            // match as regex - matcher -> matched
            try {
                if (matched.matches(this.matcher)) {
                    result = true;
                }
            } catch (PatternSyntaxException pse) {
                logger.error("Error while matching regex [" + this.matcher + "] for string [" + matched + "] " + pse.getMessage());
            }
            // match as regex - matched -> matcher
            try {
                if (this.matcher.matches(matched)) {
                    result = true;
                }
            } catch (PatternSyntaxException pse) {
                logger.error("Error while matching regex [" + matched + "] for string [" + this.matcher + "] " + pse.getMessage());
            }
            // match as xpath - matcher -> matched
            if (xpathExpression != null) {
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
