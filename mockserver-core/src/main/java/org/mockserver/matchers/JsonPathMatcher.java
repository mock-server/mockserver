package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;

import static org.slf4j.event.Level.TRACE;

/**
 * See https://github.com/json-path/JsonPath
 *
 * @author jamesdbloom
 */
public class JsonPathMatcher extends BodyMatcher<String> {
    private static final String[] EXCLUDED_FIELDS = {"key", "mockServerLogger", "jsonPath"};
    private final MockServerLogger mockServerLogger;
    private final String matcher;
    private JsonPath jsonPath;

    public JsonPathMatcher(MockServerLogger mockServerLogger, String matcher) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;
        if (StringUtils.isNotBlank(matcher)) {
            try {
                jsonPath = JsonPath.compile(matcher);
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.TRACE)
                        .setLogLevel(TRACE)
                        .setMessageFormat("Error while creating xpath expression for [" + matcher + "] assuming matcher not xpath - " + throwable.getMessage())
                        .setArguments(throwable)
                );
            }
        }
    }

    public boolean matches(final HttpRequest context, String matched) {
        boolean result = false;

        if (jsonPath == null) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.TRACE)
                    .setLogLevel(TRACE)
                    .setHttpRequest(context)
                    .setMessageFormat("Attempting match against null XPath Expression for [" + matched + "]" + new RuntimeException("Attempting match against null XPath Expression for [" + matched + "]"))
            );
        } else if (matcher.equals(matched)) {
            result = true;
        } else if (matched != null) {
            try {
                result = !jsonPath.<JSONArray>read(matched).isEmpty();
            } catch (Exception e) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.TRACE)
                        .setLogLevel(TRACE)
                        .setHttpRequest(context)
                        .setMessageFormat("Failed to match JSON: {}with JsonPath: {}because: {}")
                        .setArguments(matched, jsonPath, e.getMessage())
                );
            }
        }

        if (!result) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.TRACE)
                    .setLogLevel(TRACE)
                    .setMessageFormat("Failed to match [{}] with [{}]")
                    .setArguments(matched, matcher)
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
