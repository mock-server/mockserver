package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;

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

    public boolean matches(final HttpRequest context, String matched) {
        boolean result = false;
        boolean alreadyLoggedMatchFailure = false;

        if (jsonPath == null) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(DEBUG)
                    .setHttpRequest(context)
                    .setMessageFormat("attempting match against null json path expression for [" + matched + "]")
                    .setThrowable(new RuntimeException("Attempting match against null json path expression for [" + matched + "]"))
            );
        } else if (matcher.equals(matched)) {
            result = true;
        } else if (matched != null) {
            try {
                result = !jsonPath.<JSONArray>read(matched).isEmpty();
            } catch (Exception e) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(DEBUG)
                        .setHttpRequest(context)
                        .setMessageFormat("failed to perform json path match of{}with{}because{}")
                        .setArguments(matched, jsonPath, e.getMessage())
                );
                alreadyLoggedMatchFailure = true;

            }
        }

        if (!result && !alreadyLoggedMatchFailure) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(TRACE)
                    .setMessageFormat("failed to perform json path match of{}with{}")
                    .setArguments(matched, this.matcher)
            );
        }

        return not != result;
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }

}
