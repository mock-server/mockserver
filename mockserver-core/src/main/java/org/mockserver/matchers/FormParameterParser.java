package org.mockserver.matchers;

import io.netty.handler.codec.http.QueryStringDecoder;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Parameters;
import org.slf4j.event.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class FormParameterParser {

    private final MockServerLogger mockServerLogger;

    public FormParameterParser(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public Parameters retrieveFormParameters(String parameterString) {
        Parameters parameters = new Parameters();
        Map<String, List<String>> parameterMap = new HashMap<>();
        if (isNotBlank(parameterString)) {
            try {
                if (parameterString.contains("?")) {
                    parameterMap.putAll(new QueryStringDecoder(parameterString).parameters());
                } else {
                    parameterMap.putAll(new QueryStringDecoder("?" + parameterString).parameters());
                }
            } catch (IllegalArgumentException iae) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception{}while parsing query string{}")
                        .setArguments(parameterString, iae.getMessage())
                        .setThrowable(iae)
                );
            }
        }
        return parameters.withEntries(parameterMap);
    }

}
