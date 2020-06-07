package org.mockserver.matchers;

import org.mockserver.model.HttpRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.mockserver.configuration.ConfigurationProperties.detailedMatchFailures;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;

public class MatchDifference {

    private final HttpRequest httpRequest;
    private final Map<String, List<String>> differences = new ConcurrentHashMap<>();
    private String fieldName;

    public MatchDifference(HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
    }

    @SuppressWarnings("UnusedReturnValue")
    public MatchDifference addDifference(String messageFormat, Object... arguments) {
        if (detailedMatchFailures()) {
            if (isNotEmpty(messageFormat) && arguments != null && isNotEmpty(fieldName)) {
                differences
                    .computeIfAbsent(fieldName, key -> new ArrayList<>())
                    .add(formatLogMessage(1, messageFormat, arguments));
            }
        }
        return this;
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    @SuppressWarnings("UnusedReturnValue")
    protected MatchDifference currentField(String fieldName) {
        this.fieldName = fieldName;
        return this;
    }

    public List<String> getDifferences(String fieldName) {
        return differences.get(fieldName);
    }

}
