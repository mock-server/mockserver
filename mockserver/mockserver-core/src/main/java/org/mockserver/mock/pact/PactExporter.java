package org.mockserver.mock.pact;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Body;
import org.mockserver.model.Header;
import org.mockserver.model.Headers;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonSchemaBody;
import org.mockserver.model.NottableDateTimeString;
import org.mockserver.model.NottableSchemaString;
import org.mockserver.model.NottableString;
import org.mockserver.model.Parameter;
import org.mockserver.model.Parameters;
import org.mockserver.model.XPathBody;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Exports MockServer response expectations as a
 * <a href="https://docs.pact.io/">Pact</a> v3 consumer contract (JSON), so traffic mocked or
 * recorded in MockServer can be published to a Pact Broker / PactFlow and used for
 * consumer-driven contract testing.
 *
 * <p>Only expectations with an {@link HttpResponse} action and a concrete {@link HttpRequest}
 * matcher are exported — forward, callback, and template actions have no Pact equivalent and are
 * skipped. Matcher values are emitted as concrete example values (Pact's default exact matching).
 *
 * <h2>matchingRules</h2>
 * Non-literal MockServer matchers are translated into a Pact v3 interaction-level
 * {@code matchingRules} object (categories {@code path} / {@code query} / {@code header} /
 * {@code body}, split across {@code matchingRules.request} and {@code matchingRules.response}), the
 * inverse of the mapping in {@link PactImporter}:
 * <ul>
 *   <li>a path/query/header value that MockServer would treat as a regex (it contains a regex
 *       metacharacter) &rarr; {@code {"match":"regex","regex":"<value>"}};</li>
 *   <li>a {@link NottableSchemaString} param/header (a JSON-schema matcher) &rarr;
 *       {@code {"match":"type"}} (or {@code integer}/{@code number}/{@code decimal}/{@code boolean}
 *       per the schema {@code type});</li>
 *   <li>a {@link JsonSchemaBody} request/response body &rarr; one {@code {"match":"type"}} per
 *       top-level schema property (keyed {@code $.field}), or a single {@code $} rule when the
 *       schema declares no properties;</li>
 *   <li>an {@link XPathBody} request/response body &rarr; a body-category {@code regex} rule keyed
 *       by the XPath expression.</li>
 * </ul>
 * A purely literal interaction emits no {@code matchingRules} object at all, so its output is
 * byte-identical to before this mapping existed (Pact's default is exact matching).
 */
public class PactExporter {

    private static final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private static final Pattern UUID_PATTERN =
        Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /**
     * @param expectations the expectations to export (typically the active expectations)
     * @param consumer     the consumer name (defaults to "consumer" when blank)
     * @param provider     the provider name (defaults to "provider" when blank)
     * @return the Pact contract as pretty-printed JSON
     */
    public String export(List<Expectation> expectations, String consumer, String provider) {
        final ObjectNode root = objectMapper.createObjectNode();
        root.putObject("consumer").put("name", blankToDefault(consumer, "consumer"));
        root.putObject("provider").put("name", blankToDefault(provider, "provider"));

        final ArrayNode interactions = root.putArray("interactions");
        for (final Expectation expectation : expectations) {
            if (!(expectation.getHttpRequest() instanceof HttpRequest)) {
                continue;
            }
            final HttpRequest request = (HttpRequest) expectation.getHttpRequest();
            // a notted method/path matcher (match anything *except*) has no positive Pact equivalent
            if (isNotted(request.getMethod()) || isNotted(request.getPath())) {
                continue;
            }
            final HttpResponse response = representativeResponse(expectation);
            if (response == null) {
                // only response actions (single or sequence) have a Pact equivalent
                continue;
            }
            final ObjectNode interaction = interactions.addObject();
            interaction.put("description", describe(expectation, request));
            // round-trip the Pact provider state (the "given ..." precondition) when this
            // expectation was gated on one (see PactImporter / PactProviderStates)
            if (PactProviderStates.SCENARIO_NAME.equals(expectation.getScenarioName())
                && expectation.getScenarioState() != null) {
                interaction.putArray("providerStates")
                    .addObject()
                    .put("name", expectation.getScenarioState());
            }
            final MatchingRules requestRules = new MatchingRules();
            final MatchingRules responseRules = new MatchingRules();
            interaction.set("request", buildRequest(request, requestRules));
            interaction.set("response", buildResponse(response, responseRules));

            // emit matchingRules only when at least one non-literal matcher produced a rule, so a
            // purely literal interaction stays byte-identical to the pre-matchingRules output
            if (!requestRules.isEmpty() || !responseRules.isEmpty()) {
                final ObjectNode matchingRules = interaction.putObject("matchingRules");
                if (!requestRules.isEmpty()) {
                    matchingRules.set("request", requestRules.toNode());
                }
                if (!responseRules.isEmpty()) {
                    matchingRules.set("response", responseRules.toNode());
                }
            }
        }

        root.putObject("metadata").putObject("pactSpecification").put("version", "3.0.0");

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize Pact contract", e);
        }
    }

    private ObjectNode buildRequest(HttpRequest request, MatchingRules rules) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put("method", request.getMethod() != null ? request.getMethod().getValue() : "GET");
        final NottableString path = request.getPath();
        node.put("path", path != null ? path.getValue() : "/");
        // the path category lives directly under matchingRules.request.path (no field key)
        final ObjectNode pathRule = ruleForValue(path);
        if (pathRule != null) {
            rules.setPathRule(pathRule);
        }
        final ObjectNode query = queryToNode(request.getQueryStringParameters(), rules);
        if (query != null) {
            node.set("query", query);
        }
        final ObjectNode headers = headersToNode(request.getHeaders(), rules);
        if (headers != null) {
            node.set("headers", headers);
        }
        if (request.getBody() != null) {
            setBody(node, request.getBody(), request.getBodyAsText(), rules);
        }
        return node;
    }

    private ObjectNode buildResponse(HttpResponse response, MatchingRules rules) {
        final ObjectNode node = objectMapper.createObjectNode();
        node.put("status", response.getStatusCode() != null ? response.getStatusCode() : 200);
        final ObjectNode headers = headersToNode(response.getHeaders(), rules);
        if (headers != null) {
            node.set("headers", headers);
        }
        if (response.getBody() != null) {
            setBody(node, response.getBody(), response.getBodyAsText(), rules);
        }
        return node;
    }

    /**
     * Sets the "body" field, parsing JSON bodies into a JSON node so the Pact contract carries
     * structured content rather than an escaped string; falls back to the raw string otherwise.
     * Schema/XPath matcher bodies additionally contribute a body-category matchingRule.
     */
    private void setBody(ObjectNode node, Body<?> body, String bodyAsString, MatchingRules rules) {
        if (body instanceof JsonSchemaBody) {
            // the matcher is a JSON schema, not an example body: emit per-property type rules and do
            // not write the schema text itself into the "body" example field
            addJsonSchemaBodyRules((JsonSchemaBody) body, rules);
            return;
        }
        if (body instanceof XPathBody) {
            // XML body matched by XPath: a body-category regex rule keyed by the XPath expression.
            // The XPath is not an example body, so no "body" field is written.
            final String xpath = ((XPathBody) body).getValue();
            if (!isBlank(xpath)) {
                rules.addBodyRule(xpath, regexRule(xpath));
            }
            return;
        }
        if (isBlank(bodyAsString)) {
            return;
        }
        try {
            final JsonNode parsed = objectMapper.readTree(bodyAsString);
            node.set("body", parsed);
        } catch (JsonProcessingException notJson) {
            node.put("body", bodyAsString);
        }
    }

    /**
     * Walks a {@link JsonSchemaBody}'s top-level {@code properties} and emits one {@code {"match":"type"}}
     * rule per property keyed {@code $.field}; when the schema declares no properties (e.g. a scalar
     * schema or one that does not parse) a single {@code $} type rule is emitted instead. A one-level
     * walk is the simpler correct option here: the importer relaxes any {@code type} rule to a
     * presence regex regardless of nesting depth, so deeper recursion would not improve round-trip
     * fidelity.
     */
    private void addJsonSchemaBodyRules(JsonSchemaBody body, MatchingRules rules) {
        JsonNode schema = null;
        try {
            schema = objectMapper.readTree(body.getValue());
        } catch (Exception ignored) {
            // unparseable schema falls through to the single "$" type rule below
        }
        final JsonNode properties = schema != null ? schema.path("properties") : null;
        if (properties != null && properties.isObject() && !properties.isEmpty()) {
            final Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
            while (fields.hasNext()) {
                final Map.Entry<String, JsonNode> entry = fields.next();
                final String propertyType = entry.getValue().path("type").asText(null);
                rules.addBodyRule("$." + entry.getKey(), typeRule(propertyType));
            }
        } else {
            rules.addBodyRule("$", typeRule(schema != null ? schema.path("type").asText(null) : null));
        }
    }

    private ObjectNode headersToNode(Headers headers, MatchingRules rules) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        final ObjectNode node = objectMapper.createObjectNode();
        for (final Header header : headers.getEntries()) {
            if (isNotted(header.getName())) {
                continue; // a notted header-name matcher has no positive Pact equivalent
            }
            final String name = header.getName().getValue();
            final ArrayNode values = node.putArray(name);
            int index = 0;
            for (final NottableString value : header.getValues()) {
                if (!isNotted(value)) {
                    values.add(value.getValue());
                    final ObjectNode rule = ruleForValue(value);
                    if (rule != null) {
                        // Pact v3 keys a header rule by the JSON-path-ish $['Name'][index]
                        rules.addHeaderRule("$['" + name + "'][" + index + "]", rule);
                    }
                    index++;
                }
            }
        }
        return node;
    }

    private ObjectNode queryToNode(Parameters parameters, MatchingRules rules) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }
        final ObjectNode node = objectMapper.createObjectNode();
        for (final Parameter parameter : parameters.getEntries()) {
            if (isNotted(parameter.getName())) {
                continue; // a notted query-parameter-name matcher has no positive Pact equivalent
            }
            final String name = parameter.getName().getValue();
            final ArrayNode values = node.putArray(name);
            int index = 0;
            for (final NottableString value : parameter.getValues()) {
                if (!isNotted(value)) {
                    values.add(value.getValue());
                    final ObjectNode rule = ruleForValue(value);
                    if (rule != null) {
                        // Pact v3 keys a query rule by the JSON-path-ish $.name[index]
                        rules.addQueryRule("$." + name + "[" + index + "]", rule);
                    }
                    index++;
                }
            }
        }
        return node;
    }

    /**
     * Returns the matchingRule for a single matcher value, or null when the value is a plain literal
     * (Pact's exact-match default — no rule needed). A {@link NottableSchemaString} becomes a
     * {@code type} rule derived from the schema {@code type}; any other value that MockServer would
     * treat as a regex (contains a regex metacharacter) becomes a {@code regex} rule. An optional or
     * blank value yields no rule — the example value is kept as-is.
     */
    private ObjectNode ruleForValue(NottableString value) {
        if (value == null || value.isOptional() || isBlank(value.getValue())) {
            return null;
        }
        if (value instanceof NottableSchemaString) {
            // the value carries a JSON schema string (e.g. {"type":"integer"}); derive the Pact
            // match kind from its declared type, defaulting to the generic "type" rule
            return typeRule(schemaTypeOf(value.getValue()));
        }
        if (value instanceof NottableDateTimeString) {
            // Pact has no date-time comparison, so the closest rule is "any value of this type"
            return typeRule(null);
        }
        return looksLikeRegex(value.getValue()) ? regexRule(value.getValue()) : null;
    }

    /** Extracts the top-level {@code type} from a JSON-schema string, or null if absent/unparseable. */
    private String schemaTypeOf(String schema) {
        if (isBlank(schema)) {
            return null;
        }
        try {
            return objectMapper.readTree(schema).path("type").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private ObjectNode regexRule(String regex) {
        final ObjectNode rule = objectMapper.createObjectNode();
        rule.put("match", "regex");
        rule.put("regex", regex);
        return rule;
    }

    /**
     * Builds a Pact {@code type}-family rule for the given JSON-schema {@code type}. Pact v3 carries
     * {@code integer}/{@code number}/{@code decimal}/{@code boolean} as their own match kinds and
     * everything else (including a missing type) as the generic {@code type} rule.
     */
    private ObjectNode typeRule(String schemaType) {
        final ObjectNode rule = objectMapper.createObjectNode();
        final String match;
        if (schemaType == null) {
            match = "type";
        } else {
            switch (schemaType.toLowerCase()) {
                case "integer":
                    match = "integer";
                    break;
                case "number":
                    match = "number";
                    break;
                case "boolean":
                    match = "boolean";
                    break;
                default:
                    match = "type";
            }
        }
        rule.put("match", match);
        return rule;
    }

    /**
     * Returns true when the value contains any regex metacharacter (any of:
     * {@code \ . [ ] ( ) * + ? ^ $ |} or {@code { }}), mirroring how MockServer's
     * {@code RegexStringMatcher} decides whether a matcher value is a literal or a regex. A value
     * with none of these characters can only behave as a literal, so it needs no Pact rule.
     */
    private static boolean looksLikeRegex(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            switch (s.charAt(i)) {
                case '\\':
                case '.':
                case '[':
                case ']':
                case '{':
                case '}':
                case '(':
                case ')':
                case '*':
                case '+':
                case '?':
                case '^':
                case '$':
                case '|':
                    return true;
                default:
                    // continue scanning
            }
        }
        return false;
    }

    /** Returns the single response, or the first of a response sequence, or null for non-response actions. */
    private static HttpResponse representativeResponse(Expectation expectation) {
        if (expectation.getHttpResponse() != null) {
            return expectation.getHttpResponse();
        }
        final List<HttpResponse> responses = expectation.getHttpResponses();
        return (responses != null && !responses.isEmpty()) ? responses.get(0) : null;
    }

    /**
     * Builds a human-readable interaction description. Auto-generated UUID expectation ids make poor
     * Pact interaction labels, so falls back to "METHOD path" unless a meaningful id was set.
     */
    private static String describe(Expectation expectation, HttpRequest request) {
        final String id = expectation.getId();
        if (id != null && !UUID_PATTERN.matcher(id).matches()) {
            return id;
        }
        final String method = request.getMethod() != null ? request.getMethod().getValue() : "GET";
        final String path = request.getPath() != null ? request.getPath().getValue() : "/";
        return method + " " + path;
    }

    private static boolean isNotted(NottableString value) {
        return value != null && value.isNot();
    }

    private static String blankToDefault(String value, String fallback) {
        return (value == null || value.trim().isEmpty()) ? fallback : value;
    }

    /**
     * Accumulates the per-category Pact v3 matchingRules for one side (request or response) of an
     * interaction. Each category ({@code path} / {@code query} / {@code header} / {@code body}) is
     * created lazily, so a side with no rules contributes nothing and the interaction omits the
     * whole {@code matchingRules} object. The path category holds a single {@code matchers} array;
     * the query/header/body categories key each rule by a Pact JSON-path-ish expression, matching
     * the structure {@link PactImporter} reads back.
     */
    private final class MatchingRules {

        private ObjectNode path;
        private ObjectNode query;
        private ObjectNode header;
        private ObjectNode body;

        private boolean isEmpty() {
            return path == null && query == null && header == null && body == null;
        }

        private void setPathRule(ObjectNode rule) {
            if (path == null) {
                path = objectMapper.createObjectNode();
                path.putArray("matchers");
            }
            ((ArrayNode) path.get("matchers")).add(rule);
        }

        private void addQueryRule(String key, ObjectNode rule) {
            if (query == null) {
                query = objectMapper.createObjectNode();
            }
            query.putObject(key).putArray("matchers").add(rule);
        }

        private void addHeaderRule(String key, ObjectNode rule) {
            if (header == null) {
                header = objectMapper.createObjectNode();
            }
            header.putObject(key).putArray("matchers").add(rule);
        }

        private void addBodyRule(String key, ObjectNode rule) {
            if (body == null) {
                body = objectMapper.createObjectNode();
            }
            body.putObject(key).putArray("matchers").add(rule);
        }

        private ObjectNode toNode() {
            final ObjectNode node = objectMapper.createObjectNode();
            if (path != null) {
                node.set("path", path);
            }
            if (query != null) {
                node.set("query", query);
            }
            if (header != null) {
                node.set("header", header);
            }
            if (body != null) {
                node.set("body", body);
            }
            return node;
        }
    }
}
