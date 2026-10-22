package org.mockserver.matchers;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Produces short, actionable remediation hints from match-difference fields.
 * Each hint is a deterministic one-liner that tells the caller exactly what to change.
 */
public class MismatchRemediation {

    // matches patterns like: expected 'POST' but was 'GET' or expected "X" but was "Y" or expected X but was Y
    private static final Pattern EXPECTED_BUT_WAS_PATTERN =
        Pattern.compile("expected\\s+['\"]?([^'\"]+?)['\"]?\\s+but\\s+was\\s+['\"]?([^'\"]+?)['\"]?\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Pattern MISSING_FIELD_PATTERN =
        Pattern.compile("(?:request|multimap)\\s+(?:did not contain|does not contain|has no entry for).*?\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private MismatchRemediation() {
        // utility class
    }

    /**
     * Returns a remediation hint for the given field and its difference messages,
     * or an empty string when no specific hint can be determined.
     */
    public static String hint(MatchDifference.Field field, List<String> differences) {
        if (field == null || differences == null || differences.isEmpty()) {
            return "";
        }

        String combined = String.join(" ", differences);

        switch (field) {
            case METHOD:
                return methodHint(combined);
            case PATH:
                return pathHint(combined);
            case HEADERS:
                return headerHint(combined);
            case QUERY_PARAMETERS:
                return queryParamHint(combined);
            case COOKIES:
                return cookieHint(combined);
            case BODY:
                return bodyHint(combined);
            case PATH_PARAMETERS:
                return "check path parameter values";
            case SECURE:
                return "check whether the request uses HTTPS vs HTTP";
            case PROTOCOL:
                return "check the protocol version";
            case KEEP_ALIVE:
                return "check the keep-alive setting";
            case CLIENT_IP:
                return "check the client IP address: it is the connection's peer address, not X-Forwarded-For";
            case OPERATION:
            case OPENAPI:
                return "check the OpenAPI operation id";
            default:
                return "";
        }
    }

    /**
     * Produces a map of field to hint string for all fields that have differences.
     */
    public static java.util.Map<MatchDifference.Field, String> allHints(Map<MatchDifference.Field, List<String>> differences) {
        java.util.Map<MatchDifference.Field, String> hints = new java.util.LinkedHashMap<>();
        if (differences == null) {
            return hints;
        }
        for (Map.Entry<MatchDifference.Field, List<String>> entry : differences.entrySet()) {
            String h = hint(entry.getKey(), entry.getValue());
            if (!h.isEmpty()) {
                hints.put(entry.getKey(), h);
            }
        }
        return hints;
    }

    // --- field-specific heuristics ---

    private static String methodHint(String combined) {
        Matcher m = EXPECTED_BUT_WAS_PATTERN.matcher(combined);
        if (m.find()) {
            return "use method " + m.group(1).trim().toUpperCase() + " not " + m.group(2).trim().toUpperCase();
        }
        // Try to extract method names from generic diff text
        String upper = combined.toUpperCase();
        for (String method : new String[]{"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"}) {
            if (upper.contains(method)) {
                return "check the HTTP method (expected " + method + ")";
            }
        }
        return "check the HTTP method";
    }

    private static String pathHint(String combined) {
        // trailing slash difference
        Matcher m = EXPECTED_BUT_WAS_PATTERN.matcher(combined);
        if (m.find()) {
            String expected = m.group(1).trim();
            String actual = m.group(2).trim();
            if (pathsDifferOnlyByTrailingSlash(expected, actual)) {
                if (expected.endsWith("/")) {
                    return "add trailing slash: send " + expected + " not " + actual;
                } else {
                    return "remove trailing slash: send " + expected + " not " + actual;
                }
            }
            return "use path " + expected + " not " + actual;
        }
        return "check the request path";
    }

    static boolean pathsDifferOnlyByTrailingSlash(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String aTrimmed = a.endsWith("/") ? a.substring(0, a.length() - 1) : a;
        String bTrimmed = b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
        return aTrimmed.equals(bTrimmed) && !a.equals(b);
    }

    private static String headerHint(String combined) {
        // case mismatch detection
        Matcher em = EXPECTED_BUT_WAS_PATTERN.matcher(combined);
        if (em.find()) {
            String expected = em.group(1).trim();
            String actual = em.group(2).trim();
            if (expected.equalsIgnoreCase(actual) && !expected.equals(actual)) {
                return "header name case mismatch: send " + expected + " not " + actual;
            }
        }
        // missing header
        Matcher mh = MISSING_FIELD_PATTERN.matcher(combined);
        if (mh.find()) {
            return "add missing header " + mh.group(1);
        }
        if (combined.toLowerCase().contains("content-type") || combined.toLowerCase().contains("content_type")) {
            return "check the Content-Type header value";
        }
        return "check request headers";
    }

    private static String queryParamHint(String combined) {
        Matcher em = EXPECTED_BUT_WAS_PATTERN.matcher(combined);
        if (em.find()) {
            String expected = em.group(1).trim();
            String actual = em.group(2).trim();
            if (expected.equalsIgnoreCase(actual) && !expected.equals(actual)) {
                return "query parameter name case mismatch: send " + expected + " not " + actual;
            }
        }
        Matcher mp = MISSING_FIELD_PATTERN.matcher(combined);
        if (mp.find()) {
            return "add missing query parameter " + mp.group(1);
        }
        return "check query string parameters";
    }

    private static String cookieHint(String combined) {
        Matcher em = EXPECTED_BUT_WAS_PATTERN.matcher(combined);
        if (em.find()) {
            String expected = em.group(1).trim();
            String actual = em.group(2).trim();
            if (expected.equalsIgnoreCase(actual) && !expected.equals(actual)) {
                return "cookie name case mismatch: send " + expected + " not " + actual;
            }
        }
        Matcher mc = MISSING_FIELD_PATTERN.matcher(combined);
        if (mc.find()) {
            return "add missing cookie " + mc.group(1);
        }
        return "check request cookies";
    }

    private static String bodyHint(String combined) {
        String lower = combined.toLowerCase();
        if (lower.contains("json") && lower.contains("schema")) {
            return "request body does not match the expected JSON schema";
        }
        if (lower.contains("content-type") || lower.contains("content_type")) {
            return "check the Content-Type header and body format";
        }
        return "check the request body content";
    }
}
