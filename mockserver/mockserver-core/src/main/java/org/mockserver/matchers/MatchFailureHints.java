package org.mockserver.matchers;

import org.mockserver.model.*;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.stripEnd;

public class MatchFailureHints {

    public static List<String> generateHints(MatchDifference.Field field, Object matcher, Object matched) {
        List<String> hints = new ArrayList<>();
        switch (field) {
            case PATH:
                addPathHints(hints, matcher, matched);
                break;
            case HEADERS:
                addHeaderHints(hints, matcher, matched);
                break;
            case BODY:
                addBodyHints(hints, matcher, matched);
                break;
            default:
                break;
        }
        return hints;
    }

    private static void addPathHints(List<String> hints, Object matcher, Object matched) {
        String matcherPath = extractStringValue(matcher);
        String matchedPath = extractStringValue(matched);
        if (matcherPath == null || matchedPath == null) {
            return;
        }
        if (matcherPath.endsWith("/") != matchedPath.endsWith("/")) {
            if (stripEnd(matcherPath, "/").equals(stripEnd(matchedPath, "/"))) {
                hints.add("HINT: trailing slash mismatch - expected path " +
                    (matcherPath.endsWith("/") ? "ends with '/'" : "has no trailing '/'") +
                    " but received path " +
                    (matchedPath.endsWith("/") ? "ends with '/'" : "has no trailing '/'"));
            }
        }
        if (matcherPath.contains(".") && !matcherPath.contains("\\.") && !matcherPath.equals(matchedPath)) {
            String escaped = matcherPath.replace(".", "\\.");
            if (!escaped.equals(matcherPath)) {
                hints.add("HINT: path contains '.' which matches any character in regex - if you want a literal dot, use '\\.' instead");
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static void addHeaderHints(List<String> hints, Object matcher, Object matched) {
        if (matcher instanceof KeysToMultiValues && matched instanceof KeysToMultiValues) {
            KeysToMultiValues matcherHeaders = (KeysToMultiValues) matcher;
            KeysToMultiValues matchedHeaders = (KeysToMultiValues) matched;
            addContentTypeCharsetHint(hints, matcherHeaders, matchedHeaders);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addContentTypeCharsetHint(List<String> hints, KeysToMultiValues matcherHeaders, KeysToMultiValues matchedHeaders) {
        List<String> matcherContentTypes = getHeaderValues(matcherHeaders, "Content-Type");
        List<String> matchedContentTypes = getHeaderValues(matchedHeaders, "Content-Type");
        if (matcherContentTypes.isEmpty() || matchedContentTypes.isEmpty()) {
            return;
        }
        for (String expected : matcherContentTypes) {
            for (String actual : matchedContentTypes) {
                if (expected != null && actual != null && !expected.equals(actual)) {
                    String expectedBase = expected.split(";")[0].trim();
                    String actualBase = actual.split(";")[0].trim();
                    if (expectedBase.equalsIgnoreCase(actualBase) && !expected.contains(";") && actual.contains("charset")) {
                        hints.add("HINT: Content-Type base type '" + expectedBase +
                            "' matches but received value includes charset: '" + actual +
                            "' - consider using a substring matcher or including the charset in the expectation");
                    }
                }
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<String> getHeaderValues(KeysToMultiValues headers, String headerName) {
        List<String> values = new ArrayList<>();
        if (headers != null && headers.getEntries() != null) {
            for (Object entry : headers.getEntries()) {
                if (entry instanceof KeyToMultiValue) {
                    KeyToMultiValue header = (KeyToMultiValue) entry;
                    if (header.getName() != null && headerName.equalsIgnoreCase(header.getName().getValue())) {
                        if (header.getValues() != null) {
                            for (Object val : header.getValues()) {
                                if (val instanceof NottableString) {
                                    values.add(((NottableString) val).getValue());
                                }
                            }
                        }
                    }
                }
            }
        }
        return values;
    }

    private static void addBodyHints(List<String> hints, Object matcher, Object matched) {
        // body hints are handled by individual body matchers (JsonStringMatcher, etc.)
    }

    private static String extractStringValue(Object value) {
        if (value instanceof NottableString) {
            return ((NottableString) value).getValue();
        } else if (value instanceof String) {
            return (String) value;
        }
        return null;
    }
}
