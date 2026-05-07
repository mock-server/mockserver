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

public class JsonRpcMatcher extends BodyMatcher<String> {
    private static final String[] EXCLUDED_FIELDS = {"mockServerLogger", "objectMapper", "paramsValidator", "compiledMethodPattern"};
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final MockServerLogger mockServerLogger;
    private final String method;
    private final String paramsSchema;
    private JsonSchemaValidator paramsValidator;
    private Pattern compiledMethodPattern;

    private static final Pattern LITERAL_METHOD_PATTERN = Pattern.compile("[a-zA-Z0-9_/.-]+");

    public JsonRpcMatcher(MockServerLogger mockServerLogger, String method, String paramsSchema) {
        this.mockServerLogger = mockServerLogger;
        this.method = method;
        this.paramsSchema = paramsSchema;
        if (isNotBlank(method)) {
            if (LITERAL_METHOD_PATTERN.matcher(method).matches()) {
                compiledMethodPattern = null;
            } else {
                try {
                    compiledMethodPattern = Pattern.compile(method);
                } catch (PatternSyntaxException e) {
                    compiledMethodPattern = null;
                }
            }
        }
        if (isNotBlank(paramsSchema)) {
            paramsValidator = new JsonSchemaValidator(mockServerLogger, paramsSchema);
        }
    }

    public boolean matches(final MatchDifference context, final String matched) {
        boolean result = false;
        boolean alreadyLoggedMatchFailure = false;

        if (StringUtils.isBlank(method)) {
            if (context != null) {
                context.addDifference(mockServerLogger, "json-rpc match failed expected method:{}found:{}failed because:{}", "null", matched, "method was null");
                alreadyLoggedMatchFailure = true;
            }
        } else if (StringUtils.isBlank(matched)) {
            if (context != null) {
                context.addDifference(mockServerLogger, "json-rpc match failed expected method:{}found:{}failed because:{}", method, "null", "request body was empty");
                alreadyLoggedMatchFailure = true;
            }
        } else {
            try {
                JsonNode rootNode = OBJECT_MAPPER.readTree(matched);

                if (rootNode.isArray()) {
                    for (JsonNode element : rootNode) {
                        if (matchesSingleRequest(element, context)) {
                            result = true;
                            break;
                        }
                    }
                    if (!result && context != null) {
                        context.addDifference(mockServerLogger, "json-rpc batch match failed expected method:{}found:{}failed because:{}", method, matched, "no request in batch matched method");
                        alreadyLoggedMatchFailure = true;
                    }
                } else {
                    result = matchesSingleRequest(rootNode, context);
                    if (!result && !alreadyLoggedMatchFailure && context != null) {
                        context.addDifference(mockServerLogger, "json-rpc match failed expected method:{}found:{}failed because:{}", method, matched, "method did not match or invalid JSON-RPC format");
                        alreadyLoggedMatchFailure = true;
                    }
                }
            } catch (Throwable throwable) {
                if (context != null) {
                    context.addDifference(mockServerLogger, throwable, "json-rpc match failed expected method:{}found:{}failed because:{}", method, matched, throwable.getMessage());
                    alreadyLoggedMatchFailure = true;
                }
            }
        }

        if (!result && !alreadyLoggedMatchFailure && context != null) {
            context.addDifference(mockServerLogger, "json-rpc match failed expected method:{}found:{}", method, matched);
        }

        return not != result;
    }

    private boolean matchesSingleRequest(JsonNode node, MatchDifference context) {
        JsonNode jsonrpcNode = node.get("jsonrpc");
        if (jsonrpcNode == null || !"2.0".equals(jsonrpcNode.asText())) {
            return false;
        }

        JsonNode methodNode = node.get("method");
        if (methodNode == null || !methodMatches(methodNode.asText())) {
            return false;
        }

        if (paramsValidator != null) {
            JsonNode paramsNode = node.get("params");
            if (paramsNode == null) {
                return false;
            }
            String validation = paramsValidator.isValid(paramsNode.toString(), false);
            if (!validation.isEmpty()) {
                if (context != null) {
                    context.addDifference(mockServerLogger, "json-rpc params schema validation failed expected:{}found:{}failed because:{}", paramsSchema, paramsNode.toString(), validation);
                }
                return false;
            }
        }

        return true;
    }

    private boolean methodMatches(String actualMethod) {
        if (method.equals(actualMethod)) {
            return true;
        }
        if (compiledMethodPattern != null) {
            return compiledMethodPattern.matcher(actualMethod).matches();
        }
        return false;
    }

    public boolean isBlank() {
        return StringUtils.isBlank(method);
    }

    @Override
    @JsonIgnore
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
