package org.mockserver.codec;

import io.netty.handler.codec.http.QueryStringDecoder;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.NottableString;
import org.mockserver.model.Parameter;
import org.mockserver.model.Parameters;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.model.NottableOptionalString.optionalString;
import static org.mockserver.model.NottableString.string;

public class FormParameterDecoder {

    private static final Pattern QUOTED_PARAMETER_VALUE = Pattern.compile("^[\"']+(.*)[\"']+$");

    private final MockServerLogger mockServerLogger;

    public FormParameterDecoder(MockServerLogger mockServerLogger) {
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

    public List<NottableString> splitOnDelimiter(Parameter.Style style, List<NottableString> values) {
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
                } else {
                    for (String splitValue : value.getValue().split(style.getRegex())) {
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
