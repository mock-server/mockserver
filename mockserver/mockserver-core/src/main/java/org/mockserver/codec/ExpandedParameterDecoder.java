package org.mockserver.codec;

import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.configuration.Configuration;
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
        Map<String, List<String>> parameterMap = Collections.emptyMap();
        if (isNotBlank(parameterString)) {
            try {
                hasPath = parameterString.startsWith("/") || parameterString.contains("?") || hasPath;
                parameterMap = new QueryStringDecoder(parameterString, HttpConstants.DEFAULT_CHARSET, hasPath, Integer.MAX_VALUE, !configuration.useSemicolonAsQueryParameterSeparator()).parameters();
            } catch (IllegalArgumentException iae) {
                logUndecodable(parameterString, hasPath, iae);
            }
        }
        return parameters.withEntries(parameterMap);
    }

    public Parameters retrieveQueryParameters(String parameterString, boolean hasPath) {
        if (isNotBlank(parameterString)) {
            Map<String, List<String>> parameterMap = decodeQuery(parameterString, hasPath);
            return new Parameters().withEntries(parameterMap != null ? parameterMap : Collections.emptyMap()).withRawParameterString(rawQuery(parameterString));
        }
        return null;
    }

    private static String rawQuery(String parameterString) {
        return parameterString.contains("?") ? StringUtils.substringAfter(parameterString, "?") : parameterString;
    }

    // null when the query cannot be decoded (logged)
    private Map<String, List<String>> decodeQuery(String parameterString, boolean hasPath) {
        try {
            hasPath = parameterString.startsWith("/") || parameterString.contains("?") || hasPath;
            return new QueryStringDecoder(parameterString, HttpConstants.DEFAULT_CHARSET, parameterString.contains("/") || hasPath, Integer.MAX_VALUE, true).parameters();
        } catch (IllegalArgumentException iae) {
            logUndecodable(parameterString, hasPath, iae);
            return null;
        }
    }

    /**
     * The request carries the parameters split without decoding (decoding is what failed), so when
     * redactSecretsInLog is on the credential values of sensitive parameters are masked in the logged string and in
     * the decoder's message, which quote them raw.
     */
    private void logUndecodable(String parameterString, boolean hasPath, IllegalArgumentException iae) {
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(Level.ERROR)
                .setHttpRequest(org.mockserver.model.HttpRequest.request(hasPath || parameterString.contains("?") ? StringUtils.substringBefore(parameterString, "?") : "")
                    .withQueryStringParameters(undecodedParameters(parameterString, hasPath)))
                .setMessageFormat("exception{}while parsing query string{}")
                .setArguments(parameterString, iae.getMessage())
                .setThrowable(iae)
        );
    }

    /**
     * Sets the request's query string parameters from {@code parameterString}. When the query cannot be decoded
     * the request has no parameters, as before, and also records the raw query, so the control plane can tell
     * "undecodable" from "none".
     */
    public void applyQueryParameters(org.mockserver.model.HttpRequest httpRequest, String parameterString, boolean hasPath) {
        if (isNotBlank(parameterString)) {
            Map<String, List<String>> parameterMap = decodeQuery(parameterString, hasPath);
            String rawQuery = rawQuery(parameterString);
            httpRequest.withQueryStringParameters(new Parameters().withEntries(parameterMap != null ? parameterMap : Collections.emptyMap()).withRawParameterString(rawQuery));
            if (parameterMap == null) {
                httpRequest.withUndecodableQueryString(rawQuery);
            }
        } else {
            httpRequest.withQueryStringParameters((Parameters) null);
        }
    }

    public static Parameters undecodedParameters(String parameterString, boolean hasPath) {
        String query = parameterString.contains("?") ? StringUtils.substringAfter(parameterString, "?") : (hasPath || parameterString.startsWith("/") ? "" : parameterString);
        Parameters parameters = new Parameters();
        for (String pair : query.split("[&;]")) {
            if (!pair.isEmpty()) {
                int equals = pair.indexOf('=');
                parameters.withEntry(equals < 0 ? pair : pair.substring(0, equals), equals < 0 ? "" : pair.substring(equals + 1));
            }
        }
        return parameters;
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
                    for (String splitValue : value.getValue().split(style.getRegex().replaceAll("<name>", Matcher.quoteReplacement(Pattern.quote(name))))) {
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
