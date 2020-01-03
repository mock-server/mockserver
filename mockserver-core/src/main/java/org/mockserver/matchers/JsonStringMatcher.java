package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.JSONCompareResult;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.skyscreamer.jsonassert.JSONCompare.compareJSON;
import static org.slf4j.event.Level.DEBUG;

/**
 * @author jamesdbloom
 */
public class JsonStringMatcher extends BodyMatcher<String> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger"};
    private final MockServerLogger mockServerLogger;
    private final String matcher;
    private final MatchType matchType;

    JsonStringMatcher(MockServerLogger mockServerLogger, String matcher, MatchType matchType) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;
        this.matchType = matchType;
    }

    public boolean matches(final HttpRequest context, String matched) {
        boolean result = false;

        JSONCompareResult jsonCompareResult;
        try {
            if (isBlank(matcher)) {
                result = true;
            } else {
                JSONCompareMode jsonCompareMode = JSONCompareMode.LENIENT;
                if (matchType == MatchType.STRICT) {
                    jsonCompareMode = JSONCompareMode.STRICT;
                }
                jsonCompareResult = compareJSON(matcher, matched, jsonCompareMode);

                if (jsonCompareResult.passed()) {
                    result = true;
                }

                if (!result) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(LogEntry.LogMessageType.DEBUG)
                            .setLogLevel(DEBUG)
                            .setHttpRequest(context)
                            .setMessageFormat("failed to perform json match of{}with{}because{}")
                            .setArguments(matched, this.matcher, jsonCompareResult.getMessage())
                    );
                }
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.DEBUG)
                    .setLogLevel(DEBUG)
                    .setHttpRequest(context)
                    .setMessageFormat("failed to perform json match{}with{}because{}")
                    .setArguments(matched, this.matcher, e.getMessage())
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
