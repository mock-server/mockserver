package org.mockserver.matchers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.validator.jsonschema.JsonSchemaValidator;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class GraphQLMatcher extends BodyMatcher<String> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger", "objectMapper", "paramsValidator", "compiledOperationNamePattern"};
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final MockServerLogger mockServerLogger;
    private final String query;
    private final String operationName;
    private final String variablesSchema;
    private JsonSchemaValidator paramsValidator;
    private Pattern compiledOperationNamePattern;

    private static final Pattern LITERAL_OPERATION_NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_/.-]+");

    public GraphQLMatcher(MockServerLogger mockServerLogger, String query, String operationName, String variablesSchema) {
        this.mockServerLogger = mockServerLogger;
        this.query = query != null ? normalizeQuery(query) : null;
        this.operationName = operationName;
        this.variablesSchema = variablesSchema;
        if (isNotBlank(operationName)) {
            if (LITERAL_OPERATION_NAME_PATTERN.matcher(operationName).matches()) {
                compiledOperationNamePattern = null;
            } else {
                try {
                    compiledOperationNamePattern = Pattern.compile(operationName);
                } catch (PatternSyntaxException e) {
                    compiledOperationNamePattern = null;
                }
            }
        }
        if (isNotBlank(variablesSchema)) {
            paramsValidator = new JsonSchemaValidator(mockServerLogger, variablesSchema);
        }
    }

    public boolean matches(final MatchDifference context, final String matched) {
        boolean result = false;
        boolean alreadyLoggedMatchFailure = false;

        if (StringUtils.isBlank(query)) {
            if (context != null) {
                context.addDifference(mockServerLogger, "graphql match failed expected query:{}found:{}failed because:{}", "null", matched, "query was null");
                alreadyLoggedMatchFailure = true;
            }
        } else if (StringUtils.isBlank(matched)) {
            if (context != null) {
                context.addDifference(mockServerLogger, "graphql match failed expected query:{}found:{}failed because:{}", query, "null", "request body was empty");
                alreadyLoggedMatchFailure = true;
            }
        } else {
            try {
                JsonNode rootNode = OBJECT_MAPPER.readTree(matched);

                result = matchesSingleRequest(rootNode, context);
                if (!result && !alreadyLoggedMatchFailure && context != null) {
                    context.addDifference(mockServerLogger, "graphql match failed expected query:{}found:{}failed because:{}", query, matched, "query did not match or invalid GraphQL format");
                    alreadyLoggedMatchFailure = true;
                }
            } catch (Throwable throwable) {
                if (context != null) {
                    context.addDifference(mockServerLogger, throwable, "graphql match failed expected query:{}found:{}failed because:{}", query, matched, throwable.getMessage());
                    alreadyLoggedMatchFailure = true;
                }
            }
        }

        if (!result && !alreadyLoggedMatchFailure && context != null) {
            context.addDifference(mockServerLogger, "graphql match failed expected query:{}found:{}", query, matched);
        }

        return not != result;
    }

    private boolean matchesSingleRequest(JsonNode node, MatchDifference context) {
        JsonNode queryNode = node.get("query");
        if (queryNode == null) {
            return false;
        }

        String actualQuery = normalizeQuery(queryNode.asText());
        if (!query.equals(actualQuery)) {
            return false;
        }

        if (isNotBlank(operationName)) {
            JsonNode operationNameNode = node.get("operationName");
            if (operationNameNode == null || operationNameNode.isNull()) {
                return false;
            }
            if (!operationNameMatches(operationNameNode.asText())) {
                return false;
            }
        }

        if (paramsValidator != null) {
            JsonNode variablesNode = node.get("variables");
            if (variablesNode == null) {
                return false;
            }
            String validation = paramsValidator.isValid(variablesNode.toString(), false);
            if (!validation.isEmpty()) {
                if (context != null) {
                    context.addDifference(mockServerLogger, "graphql variables schema validation failed expected:{}found:{}failed because:{}", variablesSchema, variablesNode.toString(), validation);
                }
                return false;
            }
        }

        return true;
    }

    private boolean operationNameMatches(String actualOperationName) {
        if (operationName.equals(actualOperationName)) {
            return true;
        }
        if (compiledOperationNamePattern != null) {
            return compiledOperationNamePattern.matcher(actualOperationName).matches();
        }
        return false;
    }

    static String normalizeQuery(String query) {
        StringBuilder result = new StringBuilder(query.length());
        boolean inString = false;
        boolean inBlockString = false;
        boolean lastWasWhitespace = false;
        char[] chars = query.toCharArray();
        int len = chars.length;

        for (int i = 0; i < len; i++) {
            char c = chars[i];

            if (inBlockString) {
                if (c == '"' && i + 2 < len && chars[i + 1] == '"' && chars[i + 2] == '"') {
                    result.append("\"\"\"");
                    i += 2;
                    inBlockString = false;
                } else {
                    result.append(c);
                }
                continue;
            }

            if (inString) {
                result.append(c);
                if (c == '\\' && i + 1 < len) {
                    result.append(chars[++i]);
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                if (i + 2 < len && chars[i + 1] == '"' && chars[i + 2] == '"') {
                    lastWasWhitespace = false;
                    result.append("\"\"\"");
                    i += 2;
                    inBlockString = true;
                } else {
                    lastWasWhitespace = false;
                    result.append(c);
                    inString = true;
                }
                continue;
            }

            if (c == '#') {
                while (i < len && chars[i] != '\n') {
                    i++;
                }
                lastWasWhitespace = true;
                continue;
            }

            if (Character.isWhitespace(c) || c == ',') {
                lastWasWhitespace = true;
                continue;
            }

            boolean isPunctuation = c == '{' || c == '}' || c == '(' || c == ')' || c == ':' || c == '!' || c == '@' || c == '[' || c == ']' || c == '=' || c == '|' || c == '&' || c == '.';
            if (lastWasWhitespace && result.length() > 0 && !isPunctuation) {
                char prev = result.charAt(result.length() - 1);
                boolean prevIsPunctuation = prev == '{' || prev == '}' || prev == '(' || prev == ')' || prev == ':' || prev == '!' || prev == '@' || prev == '[' || prev == ']' || prev == '=' || prev == '|' || prev == '&' || prev == '.';
                if (!prevIsPunctuation) {
                    result.append(' ');
                }
            }
            lastWasWhitespace = false;
            result.append(c);
        }
        return result.toString();
    }

    public boolean isBlank() {
        return StringUtils.isBlank(query);
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
