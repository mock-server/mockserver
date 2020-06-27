package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.matchers.StringToXmlDocumentParser.ErrorLevel.prettyPrint;
import static org.slf4j.event.Level.DEBUG;

/**
 * @author jamesdbloom
 */
public class XPathMatcher extends BodyMatcher<String> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger", "stringToXmlDocumentParser", "xpathExpression"};
    private final MockServerLogger mockServerLogger;
    private final String matcher;
    private final StringToXmlDocumentParser stringToXmlDocumentParser = new StringToXmlDocumentParser();
    private XPathExpression xpathExpression = null;

    XPathMatcher(MockServerLogger mockServerLogger, String matcher) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;
        if (isNotBlank(matcher)) {
            try {
                xpathExpression = XPathFactory.newInstance().newXPath().compile(matcher);
            } catch (XPathExpressionException xpee) {
                if (MockServerLogger.isEnabled(DEBUG)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(DEBUG)
                            .setMessageFormat("error while creating xpath expression for [" + matcher + "] assuming matcher not xpath - " + xpee.getMessage())
                            .setArguments(xpee)
                    );
                }
            }
        }
    }

    public boolean matches(final MatchDifference context, final String matched) {
        boolean result = false;
        boolean alreadyLoggedMatchFailure = false;

        if (xpathExpression == null) {
            if (context != null) {
                context.addDifference(mockServerLogger, "xpath match failed expected:{}found:{}failed because:{}", "null", matched, "xpath matcher was null");
                alreadyLoggedMatchFailure = true;
            }
        } else if (matcher.equals(matched)) {
            result = true;
        } else if (matched != null) {
            try {
                result = (Boolean) xpathExpression.evaluate(stringToXmlDocumentParser.buildDocument(matched, (matchedInException, throwable, level) -> {
                    if (context != null) {
                        context.addDifference(mockServerLogger, throwable, "xpath match failed expected:{}found:{}failed because " + prettyPrint(level) + ":{}", matcher, matched, throwable.getMessage());
                    }
                }), XPathConstants.BOOLEAN);
            } catch (Throwable throwable) {
                if (context != null) {
                    context.addDifference(mockServerLogger, throwable, "xpath match failed expected:{}found:{}failed because:{}", matcher, matched, throwable.getMessage());
                    alreadyLoggedMatchFailure = true;
                }
            }
        }

        if (!result && !alreadyLoggedMatchFailure && context != null) {
            context.addDifference(mockServerLogger, "xpath match failed expected:{}found:{}failed because:{}", matcher, matched, "xpath did not evaluate to truthy");
        }

        return not != result;
    }

    public boolean isBlank() {
        return StringUtils.isBlank(matcher);
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
