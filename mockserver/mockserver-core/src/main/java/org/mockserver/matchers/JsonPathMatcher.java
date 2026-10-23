package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.NottableDateTimeString;

import java.util.Collection;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.slf4j.event.Level.DEBUG;

/**
 * See https://github.com/json-path/JsonPath
 *
 * @author jamesdbloom
 */
public class JsonPathMatcher extends BodyMatcher<String> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger", "jsonPath", "readsOnly"};
    private final MockServerLogger mockServerLogger;
    private final String matcher;
    private JsonPath jsonPath;
    // Jayway's append() writes to the document it reads, so a path that may call it must never read a
    // document shared with other matchers; any mention of the name opts the path out of sharing.
    private final boolean readsOnly;
    // null for a plain JSONPath body, which then runs exactly the code it did before date-time comparisons
    private final NottableDateTimeString dateTime;

    JsonPathMatcher(MockServerLogger mockServerLogger, String matcher) {
        this(mockServerLogger, matcher, null);
    }

    /**
     * @param dateTime when not null, the body matches only when a value the JSONPath selects is a
     *                 date-time that satisfies it
     */
    JsonPathMatcher(MockServerLogger mockServerLogger, String matcher, NottableDateTimeString dateTime) {
        this.mockServerLogger = mockServerLogger;
        this.matcher = matcher;
        this.dateTime = dateTime;
        this.readsOnly = matcher == null || !matcher.contains("append");
        if (isNotBlank(matcher)) {
            try {
                jsonPath = JsonPath.compile(matcher);
            } catch (Throwable throwable) {
                if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(DEBUG)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(DEBUG)
                            .setMessageFormat("error while creating xpath expression for [" + matcher + "] assuming matcher not xpath - " + throwable.getMessage())
                            .setArguments(throwable)
                    );
                }
            }
        }
    }

    public boolean matches(final MatchDifference context, final String matched) {
        return matches(context, matched, null);
    }

    /**
     * Match reading the document {@code parsedBodyCache} holds for the scan in progress, so a body
     * matched against many JSONPath expectations is parsed once rather than once per expectation.
     */
    boolean matches(final MatchDifference context, final String matched, final ParsedBodyCache parsedBodyCache) {
        boolean result = false;
        boolean alreadyLoggedMatchFailure = false;

        if (jsonPath == null) {
            if (context != null) {
                context.addDifference(mockServerLogger, "json path match failed expected:{}found:{}failed because:{}", "null", matched, "json path matcher was null");
                alreadyLoggedMatchFailure = true;
            }
        } else if (matcher.equals(matched) && dateTime == null) {
            result = true;
        } else if (matched != null) {
            try {
                Object jsonPathResult = read(matched, parsedBodyCache);
                if (dateTime != null) {
                    result = anySelectedValueIsMatchingDateTime(jsonPathResult);
                    if (!result && context != null) {
                        context.addDifference(mockServerLogger, "json path match failed expected:{}found:{}failed because:{}", matcher, matched, "no value the json path selected is a date-time " + dateTime.getValue());
                        alreadyLoggedMatchFailure = true;
                    }
                } else if (jsonPathResult instanceof Collection) {
                    result = !((Collection<?>) jsonPathResult).isEmpty();
                } else {
                    result = jsonPathResult != null;
                }
            } catch (Throwable throwable) {
                if (context != null) {
                    context.addDifference(mockServerLogger, throwable, "json path match failed expected:{}found:{}failed because:{}", matcher, matched, throwable.getMessage());
                    alreadyLoggedMatchFailure = true;
                }
            }
        }

        if (!result && !alreadyLoggedMatchFailure && context != null) {
            context.addDifference(mockServerLogger, "json path match failed expected:{}found:{}failed because:{}", matcher, matched, "json path did not evaluate to truthy");
        }

        return not != result;
    }

    /**
     * A definite path such as {@code $.created} selects one value and an indefinite one such as
     * {@code $..created} a list: the body matches when any selected value is a matching date-time. A
     * string is read as written and a whole number as epoch seconds or millis; any other value is not a
     * date-time.
     */
    private boolean anySelectedValueIsMatchingDateTime(Object jsonPathResult) {
        if (jsonPathResult instanceof Collection) {
            for (Object value : (Collection<?>) jsonPathResult) {
                if (isMatchingDateTime(value)) {
                    return true;
                }
            }
            return false;
        }
        return isMatchingDateTime(jsonPathResult);
    }

    private boolean isMatchingDateTime(Object value) {
        String text;
        if (value instanceof String) {
            text = (String) value;
        } else if (value instanceof Integer || value instanceof Long || value instanceof java.math.BigInteger) {
            text = value.toString();
        } else {
            return false;
        }
        return dateTime.matches(mockServerLogger, null, text);
    }

    private Object read(String matched, ParsedBodyCache parsedBodyCache) {
        // JsonPath.read(String) rejects an empty string, parses with the default configuration's
        // provider, then reads the document with that configuration: the same read, with the parse
        // shared. An empty body keeps the original call so it keeps its original error.
        if (parsedBodyCache == null || !readsOnly || matched.isEmpty()) {
            return jsonPath.read(matched);
        }
        Configuration configuration = Configuration.defaultConfiguration();
        return jsonPath.read(parsedBodyCache.jsonPathDocument(matched, configuration.jsonProvider()), configuration);
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
