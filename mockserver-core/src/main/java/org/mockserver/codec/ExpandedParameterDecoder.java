package org.mockserver.codec;

import io.netty.handler.codec.http.QueryStringDecoder;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.NottableString;
import org.mockserver.model.Parameter;
import org.mockserver.model.ParameterStyle;
import org.mockserver.model.Parameters;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.model.NottableOptionalString.optionalString;
import static org.mockserver.model.NottableString.string;

public class ExpandedParameterDecoder {

    private static final Pattern QUOTED_PARAMETER_VALUE = Pattern.compile("\\s*^[\"']+(.*)[\"']+\\s*$");
    private static final Pattern JSON_VALUE = Pattern.compile("(?s)^\\s*[{\\[].*[}\\]]\\s*$");

    private final MockServerLogger mockServerLogger;

    public ExpandedParameterDecoder(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public Parameters retrieveFormParameters(String parameterString, boolean hasPath) {
        Parameters parameters = new Parameters();
        Map<String, List<String>> parameterMap = new HashMap<>();
        if (isNotBlank(parameterString)) {
            try {
                parameterMap.putAll(new QueryStringDecoder(parameterString, parameterString.contains("/") || hasPath).parameters());
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

    public void splitParameters(Parameters matcher, Parameters matched) {
        if (matcher != null && matched != null) {
            for (Parameter matcherEntry : matcher.getEntries()) {
                if (matcherEntry.getName().getParameterStyle() != null && matcherEntry.getName().getParameterStyle().isExploded()) {
                    for (Parameter matchedEntry : matched.getEntries()) {
                        if (matcherEntry.getName().getValue().equals(matchedEntry.getName().getValue()) || matchedEntry.getName().getValue().matches(matcherEntry.getName().getValue())) {
                            matchedEntry.replaceValues(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(matcherEntry.getName().getParameterStyle(), matcherEntry.getName().getValue(), matchedEntry.getValues()));
                            matched.replaceEntry(matchedEntry);
                        }
                    }
                }
            }
        }
    }

    public List<NottableString> splitOnDelimiter(ParameterStyle style, String name, List<NottableString> values) {
        if (isNotBlank(style.getRegex())) {
            List<NottableString> splitValues = new ArrayList<>();
            for (NottableString value : values) {
                Matcher quotedValue = QUOTED_PARAMETER_VALUE.matcher(value.getValue());
                if (quotedValue.matches()) {
                    if (value.isOptional()) {
                        splitValues.add(optionalString(quotedValue.group(1), value.isNot()));
                    } else {
                        splitValues.add(string(quotedValue.group(1), value.isNot()));
                    }
                } else if (!JSON_VALUE.matcher(value.getValue()).matches()) {
                    for (String splitValue : value.getValue().split(style.getRegex().replaceAll("<name>", name))) {
                        if (value.isOptional()) {
                            splitValues.add(optionalString(splitValue, value.isNot()));
                        } else {
                            splitValues.add(string(splitValue, value.isNot()));
                        }
                    }
                }
            }
            return splitValues;
        } else {
            return values;
        }
    }

}
