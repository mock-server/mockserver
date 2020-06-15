package org.mockserver.matchers;

import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.mockserver.configuration.ConfigurationProperties.detailedMatchFailures;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.slf4j.event.Level.DEBUG;

public class MatchDifference {

    public enum Field {
        METHOD("method"),
        PATH("path"),
        QUERY("query"),
        COOKIES("cookies"),
        HEADERS("headers"),
        BODY("body"),
        SSL_MATCHES("sslMatches"),
        KEEP_ALIVE("keep-alive"),
        OPERATION("operation"),
        OPENAPI("openapi");

        private final String name;

        Field(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private final HttpRequest httpRequest;
    private final Map<Field, List<String>> differences = new ConcurrentHashMap<>();
    private Field fieldName;

    public MatchDifference(HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
    }

    @SuppressWarnings("UnusedReturnValue")
    public MatchDifference addDifference(MockServerLogger mockServerLogger, Throwable throwable, String messageFormat, Object... arguments) {
        if (MockServerLogger.isEnabled(DEBUG)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(DEBUG)
                    .setMessageFormat(messageFormat)
                    .setArguments(arguments)
                    .setThrowable(throwable)
            );
        }
        return addDifference(messageFormat, arguments);
    }

    @SuppressWarnings("UnusedReturnValue")
    public MatchDifference addDifference(MockServerLogger mockServerLogger, String messageFormat, Object... arguments) {
        return addDifference(mockServerLogger, null, messageFormat, arguments);
    }

    public MatchDifference addDifference(Field fieldName, String messageFormat, Object... arguments) {
        if (detailedMatchFailures()) {
            if (isNotEmpty(messageFormat) && arguments != null && isNotEmpty(fieldName)) {
                differences
                    .computeIfAbsent(fieldName, key -> new ArrayList<>())
                    .add(formatLogMessage(1, messageFormat, arguments));
            }
        }
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public MatchDifference addDifference(String messageFormat, Object... arguments) {
        return addDifference(fieldName, messageFormat, arguments);
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    @SuppressWarnings("UnusedReturnValue")
    protected MatchDifference currentField(Field fieldName) {
        this.fieldName = fieldName;
        return this;
    }

    public List<String> getDifferences(Field fieldName) {
        return differences.get(fieldName);
    }

}
