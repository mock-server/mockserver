package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.NottableDateTimeString;
import org.mockserver.xml.XPathEvaluator;
import org.w3c.dom.NodeList;

import java.util.Map;
import javax.xml.xpath.XPathConstants;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.xml.StringToXmlDocumentParser.ErrorLevel.prettyPrint;
import static org.slf4j.event.Level.DEBUG;

/**
 * @author jamesdbloom
 */
public class XPathMatcher extends BodyMatcher<String> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger", "xPathEvaluator"};
    private final MockServerLogger mockServerLogger;
    private final String matcher;
    private XPathEvaluator xPathEvaluator = null;
    // null for a plain XPath body, which then runs exactly the code it did before date-time comparisons
    private final NottableDateTimeString dateTime;

    XPathMatcher(MockServerLogger mockServerLogger, String matcher) {
        this(mockServerLogger, matcher, null);
    }

    XPathMatcher(MockServerLogger mockServerLogger, String matcher, Map<String, String> namespacePrefixes) {
        this(mockServerLogger, matcher, namespacePrefixes, null);
    }

    /**
     * @param dateTime when not null, the body matches only when the text of a node the XPath selects is a
     *                 date-time that satisfies it
     */
    XPathMatcher(MockServerLogger mockServerLogger, String matcher, Map<String, String> namespacePrefixes, NottableDateTimeString dateTime) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;
        this.dateTime = dateTime;
        if (isNotBlank(matcher)) {
            try {
                xPathEvaluator = new XPathEvaluator(matcher, namespacePrefixes,
                    mockServerLogger != null ? mockServerLogger.getConfiguration() : null);
            } catch (Throwable throwable) {
                if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(DEBUG)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(DEBUG)
                            .setMessageFormat("error while creating xpath expression for{}assuming matcher not xpath{}")
                            .setArguments(matcher, throwable.getMessage())
                            .setThrowable(throwable)
                    );
                }
            }
        }
    }

    public boolean matches(final MatchDifference context, final String matched) {
        boolean result = false;
        boolean alreadyLoggedMatchFailure = false;

        if (xPathEvaluator == null) {
            if (context != null) {
                context.addDifference(mockServerLogger, "xpath match failed expected:{}found:{}failed because:{}", "null", matched, "xpath matcher was null");
                alreadyLoggedMatchFailure = true;
            }
        } else if (matcher.equals(matched) && dateTime == null) {
            result = true;
        } else if (matched != null && dateTime != null) {
            try {
                result = anySelectedNodeIsMatchingDateTime(context, matched);
                if (!result && context != null) {
                    context.addDifference(mockServerLogger, "xpath match failed expected:{}found:{}failed because:{}", matcher, matched, "no node the xpath selected has a date-time " + dateTime.getValue());
                    alreadyLoggedMatchFailure = true;
                }
            } catch (Throwable throwable) {
                if (context != null) {
                    context.addDifference(mockServerLogger, throwable, "xpath match failed expected:{}found:{}failed because:{}", matcher, matched, throwable.getMessage());
                    alreadyLoggedMatchFailure = true;
                }
            }
        } else if (matched != null) {
            try {
                result = (Boolean) xPathEvaluator.evaluateXPathExpression(matched, (matchedInException, throwable, level) -> {
                    if (context != null) {
                        context.addDifference(mockServerLogger, throwable, "xpath match failed expected:{}found:{}failed because " + prettyPrint(level) + ":{}", matcher, matched, throwable.getMessage());
                    }
                }, XPathConstants.BOOLEAN);
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

    /**
     * The XPath must select nodes, such as {@code /order/created} or {@code //item/@expires}; the body
     * matches when the text of any of them is a matching date-time.
     */
    private boolean anySelectedNodeIsMatchingDateTime(MatchDifference context, String matched) {
        NodeList nodes = (NodeList) xPathEvaluator.evaluateXPathExpression(matched, (matchedInException, throwable, level) -> {
            if (context != null) {
                context.addDifference(mockServerLogger, throwable, "xpath match failed expected:{}found:{}failed because " + prettyPrint(level) + ":{}", matcher, matched, throwable.getMessage());
            }
        }, XPathConstants.NODESET);
        if (nodes == null) {
            return false;
        }
        for (int i = 0; i < nodes.getLength(); i++) {
            if (dateTime.matches(mockServerLogger, null, nodes.item(i).getTextContent())) {
                return true;
            }
        }
        return false;
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
