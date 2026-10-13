package org.mockserver.openapi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * Turns swagger-parser's messages into problems grouped by kind, each located by a JSON pointer
 * (such as {@code #/paths/~1pets/get/responses/200}) or, for a syntax error, by line and column.
 * <p>
 * swagger-parser names a location as a dotted path ({@code paths.'/pets'(get).responses.200}); the pointer
 * is derived from it on a best-effort basis, so it names the nearest object swagger-parser reports.
 */
public final class OpenAPILoadProblems {

    enum Kind {
        SYNTAX("syntax error"),
        UNREADABLE("spec could not be read"),
        REF("unresolved or mistyped $ref"),
        WRONG_TYPE("wrong value type"),
        MISSING("missing required field"),
        DUPLICATE("duplicate value"),
        INVALID_PATH("invalid path"),
        UNEXPECTED("unexpected or unsupported field"),
        OTHER("other problem");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /**
     * @param location a JSON pointer, "line N, column M", or null when the message names no location
     */
    record Problem(Kind kind, String location, String description, String hint) {
    }

    private static final Pattern ATTRIBUTE = Pattern.compile("^attribute (.+) is (unexpected|missing|unsupported|repeated|reserved by The OpenAPI Initiative|not of type `(.*)`)$", Pattern.DOTALL);
    private static final Pattern YAML_LINE = Pattern.compile("line (\\d+), column (\\d+)");
    private static final Pattern JSON_LINE = Pattern.compile("line: (\\d+), column: (\\d+)");
    private static final Pattern JSON_SYNTAX = Pattern.compile("^(Unexpected (end-of-input|character)|Unrecognized (token|character)|Illegal (character|unquoted)|Duplicate field|Invalid UTF)");
    private static final Pattern YAML_SYNTAX = Pattern.compile("^(while (parsing|scanning)|mapping values are not allowed|found character|expected |could not find expected|did not find expected|found undefined alias)");
    private static final Pattern INVALID_PATH = Pattern.compile("^Resource (.+) should start with /$");
    private static final Set<String> ROOT_FIELDS = Set.of("openapi", "info", "servers", "paths", "components", "webhooks", "security", "tags", "externalDocs", "jsonSchemaDialect");

    private static final String TAB_HINT = "YAML does not allow tabs for indentation: replace each tab with spaces";
    private static final String READ_HINT = "check that the file path, classpath resource or URL is correct and that MockServer can read it";
    private static final String REF_HINT = "check that the $ref target exists (names are case-sensitive) and that any referenced file can be read";
    private static final String PATH_HINT = "each key under paths must start with '/', for example /pets";
    private static final String TYPE_HINT = "in OpenAPI 3.0 a schema's type is a single string; a list of types needs OpenAPI 3.1";
    private static final String DUPLICATE_OPERATION_ID_HINT = "operationIds must be unique: MockServer keeps both operations but renames the later one by appending \" (2)\"";

    private OpenAPILoadProblems() {
    }

    static List<Problem> problems(Collection<String> messages) {
        List<Problem> problems = new ArrayList<>();
        if (messages != null) {
            for (String message : new LinkedHashSet<>(messages)) {
                if (!isBlank(message)) {
                    problems.add(problem(message.strip()));
                }
            }
        }
        return problems;
    }

    /**
     * Problems grouped by kind, for example:
     * <pre>
     * 1 problem found:
     *
     *   missing required field (1):
     *     - #/info: required field 'version' is missing
     * </pre>
     */
    public static String describe(Collection<String> messages) {
        List<Problem> problems = problems(messages);
        Map<Kind, List<Problem>> byKind = new EnumMap<>(Kind.class);
        problems.forEach(problem -> byKind.computeIfAbsent(problem.kind(), kind -> new ArrayList<>()).add(problem));
        StringBuilder text = new StringBuilder()
            .append(problems.size()).append(problems.size() == 1 ? " problem" : " problems").append(" found:");
        byKind.forEach((kind, group) -> {
            text.append(NEW_LINE).append(NEW_LINE).append("  ").append(kind.label()).append(" (").append(group.size()).append("):");
            for (Problem problem : group) {
                String line = (problem.location() != null ? problem.location() + ": " : "") + problem.description();
                text.append(NEW_LINE).append("    - ").append(line.replace("\r\n", "\n").replace("\n", NEW_LINE + "      "));
                if (problem.hint() != null) {
                    text.append(NEW_LINE).append("      hint: ").append(problem.hint());
                }
            }
        });
        return text.toString();
    }

    static Problem problem(String message) {
        if (isSyntaxError(message)) {
            String hint = message.contains("(TAB)") ? TAB_HINT : null;
            return new Problem(Kind.SYNTAX, lineAndColumn(message), message, hint);
        }
        if (message.startsWith("Unable to read location")) {
            return new Problem(Kind.UNREADABLE, null, message, READ_HINT);
        }
        if (message.startsWith("Unable to load RELATIVE ref") || message.startsWith("Unable to load REMOTE ref") || message.startsWith("Could not find")) {
            return new Problem(Kind.REF, null, message, REF_HINT);
        }
        Matcher attribute = ATTRIBUTE.matcher(message);
        if (attribute.matches()) {
            return attributeProblem(attribute.group(1), attribute.group(2), attribute.group(3));
        }
        return warning(message);
    }

    private static Problem attributeProblem(String attribute, String problem, String expectedType) {
        int refStart = attribute.indexOf(".#/");
        if (refStart >= 0 && problem.equals("missing")) {
            String ref = attribute.substring(refStart + 1);
            return new Problem(Kind.REF, pointer(tokens(attribute.substring(0, refStart)), "$ref"), "$ref target " + ref + " does not exist", REF_HINT);
        }
        List<String> tokens = tokens(attribute);
        String field = tokens.isEmpty() ? attribute : tokens.get(tokens.size() - 1);
        List<String> parent = tokens.isEmpty() ? tokens : tokens.subList(0, tokens.size() - 1);
        switch (problem) {
            case "missing":
                return new Problem(Kind.MISSING, pointer(parent, null), "required field '" + field + "' is missing", null);
            case "repeated":
                boolean operationId = field.equals("operationId");
                return new Problem(Kind.DUPLICATE, pointer(tokens, null), "'" + field + "' is repeated" + (operationId ? "" : ", it must be unique"), operationId ? DUPLICATE_OPERATION_ID_HINT : null);
            case "unexpected":
                return new Problem(Kind.UNEXPECTED, pointer(tokens, null), "field '" + field + "' is not allowed here", null);
            case "unsupported":
                return new Problem(Kind.UNEXPECTED, pointer(tokens, null), "field '" + field + "' is not supported", null);
            case "reserved by The OpenAPI Initiative":
                return new Problem(Kind.UNEXPECTED, pointer(tokens, null), "field '" + field + "' is reserved by The OpenAPI Initiative", null);
            default:
                if ("schema".equals(expectedType)) {
                    // swagger-parser's internal $ref check reports a missing #/components/schemas/<name> target this way
                    return new Problem(Kind.REF, pointer(parent, null), "$ref target #/components/schemas/" + field + " does not exist", REF_HINT);
                }
                String hint = field.equals("type") ? TYPE_HINT : null;
                return new Problem(Kind.WRONG_TYPE, pointer(tokens, null), "'" + field + "' is not of type `" + expectedType + "`", hint);
        }
    }

    /**
     * swagger-parser writes a warning as its dotted location, a '.', then free text.
     */
    private static Problem warning(String message) {
        int textStart = warningTextStart(message);
        if (textStart > 0) {
            List<String> tokens = tokens(message.substring(0, textStart - 1));
            if (!tokens.isEmpty() && ROOT_FIELDS.contains(tokens.get(0))) {
                String text = message.substring(textStart).strip();
                Matcher invalidPath = INVALID_PATH.matcher(text);
                if (invalidPath.matches()) {
                    return new Problem(Kind.INVALID_PATH, pointer(tokens, invalidPath.group(1)), "path '" + invalidPath.group(1) + "' does not start with /", PATH_HINT);
                } else if (text.startsWith("$ref target")) {
                    return new Problem(Kind.REF, pointer(tokens, null), text, REF_HINT);
                }
                return new Problem(Kind.OTHER, pointer(tokens, null), text, null);
            }
        }
        return new Problem(Kind.OTHER, null, message, null);
    }

    /**
     * @return the index just after the separator '.' that precedes the first unquoted segment holding a space, or -1
     */
    private static int warningTextStart(String message) {
        int segmentStart = 0;
        boolean quoted = false;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            } else if (!quoted && c == '.') {
                segmentStart = i + 1;
            } else if (!quoted && Character.isWhitespace(c)) {
                return segmentStart;
            }
        }
        return -1;
    }

    /**
     * Splits a swagger-parser location such as {@code paths.'/pets'(get).responses.200} into
     * {@code [paths, /pets, get, responses, 200]}.
     */
    static List<String> tokens(String location) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;
        while (i < location.length()) {
            char c = location.charAt(i);
            int close;
            if (c == '\'' && current.length() == 0 && (close = closingQuote(location, i)) > i) {
                current.append(location, i + 1, close);
                i = close + 1;
            } else if (c == '(' && (close = location.indexOf(')', i)) > i && (close + 1 == location.length() || location.charAt(close + 1) == '.')) {
                addToken(tokens, current);
                current.append(location, i + 1, close);
                addToken(tokens, current);
                i = close + 1;
            } else if (c == '.') {
                addToken(tokens, current);
                i++;
            } else {
                current.append(c);
                i++;
            }
        }
        addToken(tokens, current);
        return tokens;
    }

    private static int closingQuote(String location, int open) {
        for (int i = open + 1; i < location.length(); i++) {
            if (location.charAt(i) == '\'' && (i + 1 == location.length() || location.charAt(i + 1) == '.' || location.charAt(i + 1) == '(')) {
                return i;
            }
        }
        return -1;
    }

    private static void addToken(List<String> tokens, StringBuilder current) {
        if (current.length() > 0) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }

    /**
     * swagger-parser names a parameter list without an index, so a pointer into an operation's or path's parameters
     * stops at the parameters array.
     */
    static String pointer(List<String> tokens, String last) {
        StringBuilder pointer = new StringBuilder("#");
        for (int i = 0; i < tokens.size(); i++) {
            pointer.append('/').append(escape(tokens.get(i)));
            if (tokens.get(i).equals("parameters") && (i == 0 || !tokens.get(i - 1).equals("components"))) {
                return pointer.toString();
            }
        }
        if (last != null) {
            pointer.append('/').append(escape(last));
        }
        return pointer.toString();
    }

    private static String escape(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }

    private static boolean isSyntaxError(String message) {
        return YAML_SYNTAX.matcher(message).find() || JSON_SYNTAX.matcher(message).find() || message.contains(" in 'reader', line ") || message.contains(" in 'string', line ");
    }

    private static String lineAndColumn(String message) {
        String location = null;
        for (Pattern pattern : List.of(YAML_LINE, JSON_LINE)) {
            Matcher matcher = pattern.matcher(message);
            while (matcher.find()) {
                location = "line " + matcher.group(1) + ", column " + matcher.group(2);
            }
            if (location != null) {
                return location;
            }
        }
        return null;
    }
}
