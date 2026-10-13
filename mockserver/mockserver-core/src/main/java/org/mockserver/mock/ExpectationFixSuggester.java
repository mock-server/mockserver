package org.mockserver.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.codec.PathParametersDecoder;
import org.mockserver.fixture.SensitiveValueMatcher;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.matchers.MatchDifference;
import org.mockserver.matchers.MatchType;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.model.Body;
import org.mockserver.model.Cookie;
import org.mockserver.model.Cookies;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.JsonBody;
import org.mockserver.model.KeyToMultiValue;
import org.mockserver.model.KeysToMultiValues;
import org.mockserver.model.MediaType;
import org.mockserver.model.NottableString;
import org.mockserver.model.RequestDefinition;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.RequestDefinitionSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.mockserver.model.HttpRequest.request;

/**
 * Turns the fields on which a recorded request differs from an expectation into a minimal RFC 6902 JSON patch of
 * that expectation's {@code httpRequest}, as {@code PUT /mockserver/retrieve?type=active_expectations} returns it.
 * Each changed value is preceded by a {@code test} of its current value, so the patch fails to apply if a
 * value it changes was edited since. Read-only: nothing is applied here, and no match event is written.
 */
final class ExpectationFixSuggester {

    static final int MAX_COPIED_BODY_CHARACTERS = 64 * 1024;
    private static final String NO_DERIVED_VALUE = "MockServer cannot derive a value for this field from the recorded request; edit it by hand";
    private static final PathParametersDecoder PATH_PARAMETERS_DECODER = new PathParametersDecoder();

    private final ObjectMapper objectMapper;
    private final ExpectationSerializer expectationSerializer;
    private final RequestDefinitionSerializer requestDefinitionSerializer;
    private final MatcherBuilder matcherBuilder;

    ExpectationFixSuggester(ObjectMapper objectMapper, ExpectationSerializer expectationSerializer, RequestDefinitionSerializer requestDefinitionSerializer, MatcherBuilder matcherBuilder) {
        this.objectMapper = objectMapper;
        this.expectationSerializer = expectationSerializer;
        this.requestDefinitionSerializer = requestDefinitionSerializer;
        this.matcherBuilder = matcherBuilder;
    }

    /**
     * The differing field names of a {@code compareFields} node, as {@link MatchDifference.Field}s in declaration order.
     */
    static Set<MatchDifference.Field> differingFields(JsonNode comparison) {
        Set<MatchDifference.Field> fields = EnumSet.noneOf(MatchDifference.Field.class);
        JsonNode differences = comparison.path("differences");
        for (MatchDifference.Field field : MatchDifference.Field.values()) {
            if (differences.has(field.getName())) {
                fields.add(field);
            }
        }
        return fields;
    }

    /**
     * @return {@code patch} (the operations), {@code patchedFields}, {@code unpatchedFields} (each with a
     * {@code reason}) and {@code matchesAfterPatch}: whether the patched expectation matches the recorded request
     */
    ObjectNode suggest(Expectation expectation, HttpRequest recordedRequest, Set<MatchDifference.Field> differingFields, SensitiveValueMatcher sensitiveValues) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode patch = objectMapper.createArrayNode();
        ArrayNode patchedFields = objectMapper.createArrayNode();
        ArrayNode unpatchedFields = objectMapper.createArrayNode();
        boolean matchesAfterPatch = false;
        if (!(expectation.getHttpRequest() instanceof HttpRequest)) {
            unpatched(unpatchedFields, "httpRequest", "the expectation does not match on a plain HTTP request definition (for example it matches an OpenAPI operation), so it cannot be patched field by field");
        } else {
            boolean bodyTruncated = recordedRequest.containsHeader(MockServerEventLog.TRUNCATED_BODY_HEADER);
            HttpRequest actual = bodyTruncated ? recordedRequest.clone().removeHeader(MockServerEventLog.TRUNCATED_BODY_HEADER) : recordedRequest;
            HttpRequest fixed = ((HttpRequest) expectation.getHttpRequest()).clone();
            JsonNode current = requestJson(fixed);
            for (MatchDifference.Field field : differingFields) {
                if (field == MatchDifference.Field.PATH_PARAMETERS && containsText(patchedFields, MatchDifference.Field.PATH.getName())) {
                    // the path fix replaces the path template with the literal path, which drops its path parameters
                    patchedFields.add(field.getName());
                    continue;
                }
                HttpRequest candidate = fixed.clone();
                String reason = fix(field, candidate, actual, bodyTruncated);
                if (reason != null) {
                    unpatched(unpatchedFields, field.getName(), reason);
                    continue;
                }
                JsonNode next = requestJson(candidate);
                List<ObjectNode> operations = new ArrayList<>();
                diff("/httpRequest", current, next, operations);
                if (operations.isEmpty()) {
                    unpatched(unpatchedFields, field.getName(), NO_DERIVED_VALUE);
                } else if (operations.stream().anyMatch(operation -> containsSensitive(operation, sensitiveValues))) {
                    unpatched(unpatchedFields, field.getName(), "the fix would copy a value that the event log redacts, so it is not shown; edit it by hand");
                } else {
                    operations.forEach(patch::add);
                    patchedFields.add(field.getName());
                    fixed = candidate;
                    current = next;
                }
            }
            if (patch.size() > 0) {
                matchesAfterPatch = matches(current, actual);
            }
        }
        result.set("patch", patch);
        result.set("patchedFields", patchedFields);
        result.set("unpatchedFields", unpatchedFields);
        result.put("matchesAfterPatch", matchesAfterPatch);
        return result;
    }

    /**
     * Changes {@code candidate} so that {@code field} accepts {@code actual}.
     *
     * @return null when fixed, else why the field cannot be fixed automatically
     */
    private String fix(MatchDifference.Field field, HttpRequest candidate, HttpRequest actual, boolean bodyTruncated) {
        switch (field) {
            case METHOD:
                if (actual.getMethod() == null || actual.getMethod().isBlank()) {
                    return NO_DERIVED_VALUE;
                }
                candidate.withMethod(actual.getMethod().getValue());
                return null;
            case PATH:
                if (actual.getPath() == null || actual.getPath().isBlank()) {
                    return NO_DERIVED_VALUE;
                }
                candidate.withPath(actual.getPath().getValue());
                candidate.withPathParameters((org.mockserver.model.Parameters) null);
                return null;
            case PATH_PARAMETERS:
                return fixPathParameters(candidate, actual);
            case QUERY_PARAMETERS:
                candidate.withQueryStringParameters(fixEntries(candidate.getQueryStringParameters(), actual.getQueryStringParameters(), false,
                    entries -> request().withQueryStringParameters(entries), actual));
                return null;
            case HEADERS:
                candidate.withHeaders(fixEntries(candidate.getHeaders(), actual.getHeaders(), true,
                    entries -> request().withHeaders(entries), actual));
                return null;
            case COOKIES:
                candidate.withCookies(fixCookies(candidate.getCookies(), actual.getCookies(), actual));
                return null;
            case BODY:
                return fixBody(candidate, actual, bodyTruncated);
            case SECURE:
                candidate.withSecure(actual.isSecure());
                return null;
            case KEEP_ALIVE:
                candidate.withKeepAlive(actual.isKeepAlive());
                return null;
            case PROTOCOL:
                candidate.withProtocol(actual.getProtocol());
                return null;
            default:
                return NO_DERIVED_VALUE;
        }
    }

    private String fixPathParameters(HttpRequest candidate, HttpRequest actual) {
        org.mockserver.model.Parameters actualParameters;
        try {
            actualParameters = PATH_PARAMETERS_DECODER.extractPathParameters(candidate, actual);
        } catch (IllegalArgumentException iae) {
            return NO_DERIVED_VALUE;
        }
        NottableString path = candidate.getPath();
        candidate.withPathParameters(fixEntries(candidate.getPathParameters(), actualParameters, false,
            entries -> request().withPath(path).withPathParameters(entries), actual));
        return null;
    }

    /**
     * Keeps each expected entry that the request satisfies on its own; replaces the values of one it does not with the
     * request's values for that name, when that satisfies it; removes it otherwise.
     */
    private <T extends KeyToMultiValue, K extends KeysToMultiValues<T, K>> K fixEntries(K expected, K actual, boolean ignoreCase, Function<K, HttpRequest> probe, HttpRequest request) {
        if (expected == null || expected.isEmpty()) {
            return expected;
        }
        K fixed = emptyCopy(expected);
        for (T entry : expected.getEntries()) {
            if (matches(probe.apply(emptyCopy(expected).withEntry(entry)), request)) {
                fixed.withEntry(entry);
                continue;
            }
            List<NottableString> actualValues = actualValues(actual, entry.getName(), ignoreCase);
            if (!entry.getName().isNot() && !actualValues.isEmpty()) {
                T replacement = expected.build(entry.getName(), actualValues);
                if (matches(probe.apply(emptyCopy(expected).withEntry(replacement)), request)) {
                    fixed.withEntry(replacement);
                }
            }
        }
        return fixed;
    }

    private static <T extends KeyToMultiValue, K extends KeysToMultiValues<T, K>> K emptyCopy(K expected) {
        K copy = expected.clone().withEntries(Collections.<T>emptyList());
        copy.withKeyMatchStyle(expected.getKeyMatchStyle());
        return copy;
    }

    private static <T extends KeyToMultiValue, K extends KeysToMultiValues<T, K>> List<NottableString> actualValues(K actual, NottableString name, boolean ignoreCase) {
        List<NottableString> values = new ArrayList<>();
        if (actual != null) {
            for (T entry : actual.getEntries()) {
                String actualName = entry.getName().getValue();
                if (ignoreCase ? actualName.equalsIgnoreCase(name.getValue()) : actualName.equals(name.getValue())) {
                    for (NottableString value : entry.getValues()) {
                        values.add(NottableString.string(value.getValue()));
                    }
                }
            }
        }
        return values;
    }

    private Cookies fixCookies(Cookies expected, Cookies actual, HttpRequest request) {
        if (expected == null || expected.isEmpty()) {
            return expected;
        }
        Cookies fixed = new Cookies();
        for (Cookie entry : expected.getEntries()) {
            if (matches(request().withCookies(new Cookies(entry)), request)) {
                fixed.withEntry(entry);
                continue;
            }
            if (!entry.getName().isNot() && actual != null) {
                for (Cookie actualCookie : actual.getEntries()) {
                    if (actualCookie.getName().getValue().equals(entry.getName().getValue())) {
                        Cookie replacement = new Cookie(entry.getName(), NottableString.string(actualCookie.getValue().getValue()));
                        if (matches(request().withCookies(new Cookies(replacement)), request)) {
                            fixed.withEntry(replacement);
                        }
                        break;
                    }
                }
            }
        }
        return fixed;
    }

    private String fixBody(HttpRequest candidate, HttpRequest actual, boolean bodyTruncated) {
        if (bodyTruncated) {
            return "the event log kept only part of this request's body (maxLoggedBodyBytes), so it cannot be copied into the expectation";
        }
        Body<?> expectedBody = candidate.getBody();
        String actualText = actual.getBodyAsString();
        if (actualText == null || actualText.isEmpty()) {
            // the request has no body: drop the body matcher
            candidate.withBody((Body<?>) null);
            return null;
        }
        if (actualText.length() > MAX_COPIED_BODY_CHARACTERS) {
            return "the request body is larger than " + (MAX_COPIED_BODY_CHARACTERS / 1024) + " KiB, so it is not copied into a patch; edit the body matcher by hand";
        }
        if (expectedBody != null && Boolean.TRUE.equals(expectedBody.getNot())) {
            // a negated body matcher rejects this body: drop it
            candidate.withBody((Body<?>) null);
            return null;
        }
        if (expectedBody instanceof JsonBody) {
            JsonNode actualJson = readJson(actualText);
            JsonNode expectedJson = readJson(((JsonBody) expectedBody).getValue());
            if (actualJson != null && expectedJson != null) {
                JsonBody jsonBody = (JsonBody) expectedBody;
                JsonNode fixedJson = jsonBody.getMatchType() == MatchType.STRICT ? actualJson : keepMatchingFields(expectedJson, actualJson);
                JsonBody fixedBody = new JsonBody(fixedJson.toString(), null,
                    jsonBody.getContentType() != null ? MediaType.parse(jsonBody.getContentType()) : null,
                    jsonBody.getMatchType(), jsonBody.isMatchNumbersAsStrings());
                fixedBody.withOptional(jsonBody.getOptional());
                candidate.withBody(fixedBody);
                return null;
            }
        }
        candidate.withBody(actual.getBody());
        return null;
    }

    /**
     * For a body matched on its listed fields only: keeps the expected structure, with each value the request
     * contradicts replaced by the request's value and each field the request lacks removed. A json-unit placeholder
     * such as {@code ${json-unit.any-string}} is kept.
     */
    private JsonNode keepMatchingFields(JsonNode expected, JsonNode actual) {
        if (expected.isTextual() && expected.asText().startsWith("${json-unit.")) {
            return expected;
        }
        if (expected.equals(actual)) {
            return expected;
        }
        if (expected.isObject() && actual.isObject()) {
            ObjectNode fixed = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = expected.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (actual.has(field.getKey())) {
                    fixed.set(field.getKey(), keepMatchingFields(field.getValue(), actual.get(field.getKey())));
                }
            }
            return fixed;
        }
        return actual;
    }

    private JsonNode readJson(String text) {
        try {
            return text != null ? objectMapper.readTree(text) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode requestJson(HttpRequest httpRequest) {
        try {
            JsonNode node = objectMapper.readTree(expectationSerializer.serialize(new Expectation(httpRequest))).path("httpRequest");
            return node.isMissingNode() ? objectMapper.createObjectNode() : node;
        } catch (Exception e) {
            throw new IllegalStateException("could not serialise the expectation's request matcher", e);
        }
    }

    private boolean matches(HttpRequest probe, HttpRequest request) {
        HttpRequest clone = request.clone();
        return matcherBuilder.transformsToMatcher(new Expectation(probe))
            .matches(new MatchDifference(false, clone).suppressMatchResultLogging(), clone);
    }

    /**
     * Reads the patched request matcher back from JSON, as {@code PUT /mockserver/expectation} would, and matches it.
     */
    private boolean matches(JsonNode patchedRequestJson, HttpRequest request) {
        try {
            RequestDefinition patched = requestDefinitionSerializer.deserialize(objectMapper.writeValueAsString(patchedRequestJson));
            return patched instanceof HttpRequest && matches((HttpRequest) patched, request);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Appends to {@code operations} the add, remove and replace operations that turn {@code from} into {@code to},
     * each remove and replace preceded by a test of the value it changes, and each object that gains a member by a
     * test of that whole object (an add would otherwise overwrite a member added since). Arrays are replaced whole.
     */
    static void diff(String path, JsonNode from, JsonNode to, List<ObjectNode> operations) {
        if (from.equals(to)) {
            return;
        }
        if (from.isObject() && to.isObject()) {
            Iterator<String> toNames = to.fieldNames();
            while (toNames.hasNext()) {
                if (!from.has(toNames.next())) {
                    operations.add(operation("test", path, from));
                    break;
                }
            }
            Iterator<Map.Entry<String, JsonNode>> fromFields = from.fields();
            while (fromFields.hasNext()) {
                Map.Entry<String, JsonNode> field = fromFields.next();
                String fieldPath = path + "/" + escape(field.getKey());
                if (to.has(field.getKey())) {
                    diff(fieldPath, field.getValue(), to.get(field.getKey()), operations);
                } else {
                    operations.add(operation("test", fieldPath, field.getValue()));
                    operations.add(operation("remove", fieldPath, null));
                }
            }
            Iterator<Map.Entry<String, JsonNode>> toFields = to.fields();
            while (toFields.hasNext()) {
                Map.Entry<String, JsonNode> field = toFields.next();
                if (!from.has(field.getKey())) {
                    operations.add(operation("add", path + "/" + escape(field.getKey()), field.getValue()));
                }
            }
        } else {
            operations.add(operation("test", path, from));
            operations.add(operation("replace", path, to));
        }
    }

    private static ObjectNode operation(String op, String path, JsonNode value) {
        ObjectNode operation = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        operation.put("op", op);
        operation.put("path", path);
        if (value != null) {
            operation.set("value", value.deepCopy());
        }
        return operation;
    }

    private static String escape(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }

    private static boolean containsSensitive(JsonNode node, SensitiveValueMatcher sensitiveValues) {
        if (sensitiveValues == null || sensitiveValues.isEmpty()) {
            return false;
        }
        if (node.isValueNode()) {
            // numbers and booleans too: a redacted body field can hold a number such as a card number
            return sensitiveValues.containsAny(node.asText()) || sensitiveValues.containsAny(node.toString());
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                if (containsSensitive(element, sensitiveValues)) {
                    return true;
                }
            }
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (sensitiveValues.containsAny(field.getKey()) || containsSensitive(field.getValue(), sensitiveValues)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsText(ArrayNode array, String text) {
        for (JsonNode element : array) {
            if (text.equals(element.asText())) {
                return true;
            }
        }
        return false;
    }

    private static void unpatched(ArrayNode unpatchedFields, String field, String reason) {
        ObjectNode node = unpatchedFields.addObject();
        node.put("field", field);
        node.put("reason", reason);
    }
}
