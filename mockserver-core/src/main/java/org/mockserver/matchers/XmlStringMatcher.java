package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.NottableString;
import org.slf4j.event.Level;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;
import org.xmlunit.placeholder.PlaceholderDifferenceEvaluator;

import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class XmlStringMatcher extends BodyMatcher<String> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger", "diffBuilder"};
    private final MockServerLogger mockServerLogger;
    private DiffBuilder diffBuilder;
    private NottableString matcher = string("THIS SHOULD NEVER MATCH");

    XmlStringMatcher(MockServerLogger mockServerLogger, final String matcher) {
        this(mockServerLogger, string(matcher));
    }

    XmlStringMatcher(MockServerLogger mockServerLogger, final NottableString matcher) {
        this.mockServerLogger = mockServerLogger;
        try {
            this.matcher = matcher;
            this.diffBuilder = DiffBuilder.compare(Input.fromString(this.matcher.getValue()))
                .ignoreComments()
                .ignoreWhitespace()
                .normalizeWhitespace()
                .checkForSimilar();
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("error while creating xml string matcher for [" + matcher + "]" + e.getMessage())
                    .setThrowable(e)
            );
        }
    }

    public boolean matches(String matched) {
        return matches(null, matched);
    }

    public boolean matches(final MatchDifference context, String matched) {
        boolean result = false;

        if (diffBuilder != null) {
            try {
                Diff diff = diffBuilder.withTest(Input.fromString(matched)).withDifferenceEvaluator(new PlaceholderDifferenceEvaluator()).build();
                result = !diff.hasDifferences();

                if (!result && context != null) {
                    context.addDifference(mockServerLogger, "xml match failed expected:{}found:{}failed because:{}", this.matcher, matched, diff.toString());
                }

            } catch (Throwable throwable) {
                if (context != null) {
                    context.addDifference(mockServerLogger, throwable, "xml match failed expected:{}found:{}failed because:{}", this.matcher, matched, throwable.getMessage());
                }
            }
        }

        return matcher.isNot() == (not == result);
    }

    public boolean isBlank() {
        return matcher == null || StringUtils.isBlank(matcher.getValue());
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
