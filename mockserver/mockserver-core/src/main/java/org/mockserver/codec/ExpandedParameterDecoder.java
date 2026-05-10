package org.mockserver.codec;

import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.NottableString;
import org.mockserver.model.Parameter;
import org.mockserver.model.ParameterStyle;
import org.mockserver.model.Parameters;
import org.slf4j.event.Level;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.model.NottableOptionalString.optional;
import static org.mockserver.model.NottableString.string;

public class ExpandedParameterDecoder {

    private static final Pattern QUOTED_PARAMETER_VALUE = Pattern.compile("\\s*^[\"']+(.*)[\"']+\\s*$");
    private static final Pattern JSON_VALUE = Pattern.compile("(?s)^\\s*[{\\[].*[}\\]]\\s*$");

    private final Configuration configuration;
    private final MockServerLogger mockServerLogger;

    public ExpandedParameterDecoder(Configuration configuration, MockServerLogger mockServerLogger) {
        this.configuration = configuration;
        this.mockServerLogger = mockServerLogger;
    }

    public Parameters retrieveFormParameters(String parameterString, boolean hasPath) {
        Parameters parameters = new Parameters();
        Map<String, List<String>> parameterMap = new HashMap<>();
        if (isNotBlank(parameterString)) {
            try {
                hasPath = parameterString.startsWith("/") || parameterString.contains("?") || hasPath;
                parameterMap.putAll(new QueryStringDecoder(parameterString, HttpConstants.DEFAULT_CHARSET, hasPath, Integer.MAX_VALUE, !configuration.useSemicolonAsQueryParameterSeparator()).parameters());
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

    public Parameters retrieveQueryParameters(String parameterString, boolean hasPath) {
        if (isNotBlank(parameterString)) {
            String rawParameterString = parameterString.contains("?") ? StringUtils.substringAfter(parameterString, "?") : parameterString;
            Map<String, List<String>> parameterMap = new HashMap<>();
            try {
                hasPath = parameterString.startsWith("/") || parameterString.contains("?") || hasPath;
                parameterMap.putAll(new QueryStringDecoder(parameterString, HttpConstants.DEFAULT_CHARSET, parameterString.contains("/") || hasPath, Integer.MAX_VALUE, true).parameters());
            } catch (IllegalArgumentException iae) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception{}while parsing query string{}")
                        .setArguments(parameterString, iae.getMessage())
                        .setThrowable(iae)
                );
            }
            return new Parameters().withEntries(parameterMap).withRawParameterString(rawParameterString);
        }
        return null;
    }

    public void splitParameters(Parameters matcher, Parameters matched) {
        if (matcher != null && matched != null) {
            for (Parameter matcherEntry : matcher.getEntries()) {
                ParameterStyle style = matcherEntry.getName().getParameterStyle();
                String schemaType = matcherEntry.getName().getSchemaType();
                if (style != null && style == ParameterStyle.DEEP_OBJECT && "object".equals(schemaType)) {
                    reconstructDeepObjectParameter(matcherEntry, matched);
                } else if (style != null && style.isExploded()) {
                    for (Parameter matchedEntry : matched.getEntries()) {
                        if (matcherEntry.getName().getValue().equals(matchedEntry.getName().getValue()) || matchedEntry.getName().getValue().matches(matcherEntry.getName().getValue())) {
                            List<NottableString> splitValues = new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(style, matcherEntry.getName().getValue(), matchedEntry.getValues());
                            if ("object".equals(schemaType)) {
                                String json;
                                if (style.isExplodedObjectStyle()) {
                                    json = reconstructExplodedObject(splitValues);
                                } else {
                                    json = reconstructObjectFromAlternatingValues(splitValues);
                                }
                                if (json != null) {
                                    matchedEntry.replaceValues(Collections.singletonList(string(json)));
                                } else {
                                    matchedEntry.replaceValues(splitValues);
                                }
                            } else {
                                matchedEntry.replaceValues(splitValues);
                            }
                            matched.replaceEntry(matchedEntry);
                        }
                    }
                }
            }
        }
    }

    private void reconstructDeepObjectParameter(Parameter matcherEntry, Parameters matched) {
        String baseName = matcherEntry.getName().getValue();
        Pattern deepObjectPattern = Pattern.compile(Pattern.quote(baseName) + "\\[([^\\]]+)]");
        LinkedHashMap<String, String> properties = new LinkedHashMap<>();
        List<Parameter> toRemove = new ArrayList<>();
        for (Parameter matchedEntry : matched.getEntries()) {
            Matcher deepObjectMatcher = deepObjectPattern.matcher(matchedEntry.getName().getValue());
            if (deepObjectMatcher.matches()) {
                String propertyName = deepObjectMatcher.group(1);
                List<NottableString> values = matchedEntry.getValues();
                if (!values.isEmpty()) {
                    properties.put(propertyName, values.get(0).getValue());
                }
                toRemove.add(matchedEntry);
            }
        }
        if (!properties.isEmpty()) {
            for (Parameter entry : toRemove) {
                matched.remove(entry.getName().getValue());
            }
            String json = buildJsonObject(properties);
            matched.withEntry(baseName, json);
        }
    }

    String reconstructExplodedObject(List<NottableString> values) {
        LinkedHashMap<String, String> properties = new LinkedHashMap<>();
        for (NottableString value : values) {
            String val = value.getValue();
            int equalsIndex = val.indexOf('=');
            if (equalsIndex > 0) {
                properties.put(val.substring(0, equalsIndex), val.substring(equalsIndex + 1));
            } else {
                return null;
            }
        }
        if (properties.isEmpty()) {
            return null;
        }
        return buildJsonObject(properties);
    }

    String reconstructObjectFromAlternatingValues(List<NottableString> values) {
        if (values.size() < 2 || values.size() % 2 != 0) {
            return null;
        }
        LinkedHashMap<String, String> properties = new LinkedHashMap<>();
        for (int i = 0; i < values.size(); i += 2) {
            properties.put(values.get(i).getValue(), values.get(i + 1).getValue());
        }
        return buildJsonObject(properties);
    }

    private static String buildJsonObject(LinkedHashMap<String, String> properties) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(escapeJsonString(entry.getKey())).append("\":");
            sb.append("\"").append(escapeJsonString(entry.getValue())).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJsonString(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    public List<NottableString> splitOnDelimiter(ParameterStyle style, String name, List<NottableString> values) {
        if (isNotBlank(style.getRegex())) {
            List<NottableString> splitValues = new ArrayList<>();
            for (NottableString value : values) {
                Matcher quotedValue = QUOTED_PARAMETER_VALUE.matcher(value.getValue());
                if (quotedValue.matches()) {
                    if (value.isOptional()) {
                        splitValues.add(optional(quotedValue.group(1), value.isNot()));
                    } else {
                        splitValues.add(string(quotedValue.group(1), value.isNot()));
                    }
                } else if (!JSON_VALUE.matcher(value.getValue()).matches()) {
                    for (String splitValue : value.getValue().split(style.getRegex().replaceAll("<name>", name))) {
                        if (value.isOptional()) {
                            splitValues.add(optional(splitValue, value.isNot()));
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
