package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import static org.slf4j.event.Level.DEBUG;

/**
 * @author jamesdbloom
 */
public class BooleanMatcher extends ObjectWithReflectiveEqualsHashCodeToString implements Matcher<Boolean> {
    private static final String[] excludedFields = {"mockServerLogger"};
    private final MockServerLogger mockServerLogger;
    private final Boolean matcher;

    BooleanMatcher(MockServerLogger mockServerLogger, Boolean matcher) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;
    }

    @Override
    public boolean matches(final MatchDifference context, Boolean matched) {
        boolean result = false;

        if (matcher == null) {
            result = true;
        } else if (matched != null) {
            result = matched == matcher;
        }

        if (!result) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(DEBUG)
                    .setMatchDifference(context)
                    .setMessageFormat("boolean match failed expected:{}found:{}")
                    .setArguments(this.matcher, matched)
            );
        }

        return result;
    }

    public boolean isBlank() {
        return matcher == null;
    }

    @Override
    @JsonIgnore
    public String[] fieldsExcludedFromEqualsAndHashCode() {
        return excludedFields;
    }

}
