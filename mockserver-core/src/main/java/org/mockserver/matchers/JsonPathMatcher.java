package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.slf4j.event.Level.DEBUG;
import static org.slf4j.event.Level.TRACE;

/**
 * See https://github.com/json-path/JsonPath
 *
 * @author jamesdbloom
 */
public class JsonPathMatcher extends BodyMatcher<String> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger", "jsonPath"};
    private final MockServerLogger mockServerLogger;
    private final String matcher;
    private JsonPath jsonPath;

    JsonPathMatcher(MockServerLogger mockServerLogger, String matcher) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;
        if (isNotBlank(matcher)) {
            try {
                jsonPath = JsonPath.compile(matcher);
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(DEBUG)
                        .setMessageFormat("error while creating xpath expression for [" + matcher + "] assuming matcher not xpath - " + throwable.getMessage())
                        .setArguments(throwable)
                );
            }
        }
    }

    public boolean matches(final MatchDifference context, final String matched) {
        boolean result = false;
        boolean alreadyLoggedMatchFailure = false;

        if (jsonPath == null) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(DEBUG)
                    .setMatchDifference(context)
                    .setMessageFormat("json path match failed expected:{}found:{}failed because:{}")
                    .setArguments("null", matched, "json path matcher was null")
            );
            alreadyLoggedMatchFailure = true;
        } else if (matcher.equals(matched)) {
            result = true;
        } else if (matched != null) {
            try {
                result = !jsonPath.<JSONArray>read(matched).isEmpty();
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(DEBUG)
                        .setMatchDifference(context)
                        .setMessageFormat("json path match failed expected:{}found:{}failed because:{}")
                        .setArguments(matcher, matched, throwable.getMessage())
                        .setThrowable(throwable)
                );
                alreadyLoggedMatchFailure = true;
            }
        }

        if (!result && !alreadyLoggedMatchFailure) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(TRACE)
                    .setMatchDifference(context)
                    .setMessageFormat("json path match failed expected:{}found:{}failed because:{}")
                    .setArguments(matcher, matched, "json path did not evaluate to truthy")
            );
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
