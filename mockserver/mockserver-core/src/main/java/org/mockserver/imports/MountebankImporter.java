package org.mockserver.imports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.matchers.MatchType;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Header;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;
import org.mockserver.model.NottableString;
import org.mockserver.model.Parameter;
import org.mockserver.model.StringBody;
import org.mockserver.serialization.ObjectMapperFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Imports MockServer expectations from <a href="https://www.mbtest.org/">Mountebank</a> imposter
 * JSON.
 *
 * <p>Accepts a single imposter object, a {@code { "imposters": [ ... ] }} wrapper (the mb config
 * file / {@code GET /imposters?replayable=true} format), or a bare JSON array of imposters. Only
 * {@code http}/{@code https} imposters are mapped — {@code tcp}/{@code smtp} imposters are skipped
 * with a per-imposter {@link ImportWarning}.
 *
 * <h2>Mapping</h2>
 * <ul>
 *     <li>Predicates ({@code equals}/{@code deepEquals}/{@code contains}/{@code matches}/
 *         {@code exists}/{@code startsWith}/{@code endsWith}) over the {@code method}, {@code path},
 *         {@code query}, {@code headers} and {@code body} request fields become MockServer matchers;
 *         all predicates in a stub are AND-combined into one request matcher.</li>
 *     <li>{@code is} responses become {@link HttpResponse}s (statusCode/headers/body).</li>
 *     <li>{@code proxy} responses become {@link HttpForward}s (to host/port/scheme).</li>
 *     <li>A response {@code fault} becomes an {@link HttpError}.</li>
 *     <li>{@code _behaviors.wait} becomes a response delay; {@code _behaviors.repeat} is reported
 *         as an {@link ImportWarning} and otherwise dropped — a Mountebank single response wraps
 *         and is served indefinitely, so a {@code Times.exactly(N)} constraint would wrongly make
 *         the stub 404 after N matching requests.</li>
 *     <li>A stub with multiple {@code is} responses becomes one sequential/cycling multi-response
 *         expectation; mixed or non-{@code is} multi-response stubs fall back to the first response
 *         with a warning.</li>
 * </ul>
 *
 * <p>Compound predicates ({@code and}/{@code or}/{@code not}), JavaScript {@code inject} responses,
 * and post-processing behaviours ({@code decorate}/{@code copy}/{@code lookup}/{@code shellTransform})
 * have no MockServer equivalent and are reported as {@link ImportWarning}s.
 */
public class MountebankImporter {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    public ImportResult importExpectations(String imposterJson) {
        return importExpectations(imposterJson, ImportRedaction.Options.enabled());
    }

    public ImportResult importExpectations(String imposterJson, ImportRedaction.Options redactionOptions) {
        if (imposterJson == null || imposterJson.trim().isEmpty()) {
            throw new IllegalArgumentException("Mountebank imposter JSON body is required");
        }

        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(imposterJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to parse Mountebank imposter JSON: " + e.getMessage(), e);
        }

        List<JsonNode> imposters = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(imposters::add);
        } else if (root.has("imposters") && root.path("imposters").isArray()) {
            root.path("imposters").forEach(imposters::add);
        } else if (root.has("stubs") || root.has("protocol")) {
            imposters.add(root);
        } else {
            throw new IllegalArgumentException(
                "not a valid Mountebank imposter document — expected an imposter object with 'protocol'/'stubs', an 'imposters' array, or a JSON array of imposters");
        }

        List<Expectation> expectations = new ArrayList<>();
        List<ImportWarning> warnings = new ArrayList<>();
        int imposterIndex = 0;
        for (JsonNode imposter : imposters) {
            String protocol = textOrNull(imposter, "protocol");
            String port = imposter.path("port").isMissingNode() ? "?" : imposter.path("port").asText();
            String locatorBase = "imposter " + (protocol != null ? protocol : "?") + ":" + port;

            if (protocol != null && !"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                warnings.add(new ImportWarning(locatorBase, "imposter.protocol",
                    "only http/https imposters are importable — this " + protocol + " imposter was skipped"));
                imposterIndex++;
                continue;
            }

            JsonNode stubs = imposter.path("stubs");
            if (!stubs.isArray()) {
                warnings.add(new ImportWarning(locatorBase, "imposter.stubs",
                    "no 'stubs' array present — nothing to import for this imposter"));
                imposterIndex++;
                continue;
            }

            int stubIndex = 0;
            for (JsonNode stub : stubs) {
                String locator = locatorBase + " stub[" + stubIndex + "]";
                Expectation expectation = buildExpectation(stub, imposterIndex, stubIndex, locator, warnings);
                if (expectation != null) {
                    expectations.add(expectation);
                }
                stubIndex++;
            }
            imposterIndex++;
        }

        return new ImportResult(ImportRedaction.redactPreservingActions(expectations, redactionOptions), warnings);
    }

    private Expectation buildExpectation(JsonNode stub, int imposterIndex, int stubIndex, String locator, List<ImportWarning> warnings) {
        HttpRequest httpRequest = request();
        JsonNode predicates = stub.path("predicates");
        if (predicates.isArray()) {
            for (JsonNode predicate : predicates) {
                applyPredicate(httpRequest, predicate, locator, warnings);
            }
        }

        JsonNode responses = stub.path("responses");
        List<JsonNode> responseList = new ArrayList<>();
        if (responses.isArray()) {
            responses.forEach(responseList::add);
        }

        String id = "mountebank-" + imposterIndex + "-" + stubIndex;

        if (responseList.isEmpty()) {
            warnings.add(new ImportWarning(locator, "stub.responses",
                "stub has no responses — a default 200 response was generated"));
            return new Expectation(httpRequest).withId(id).thenRespond(response().withStatusCode(200));
        }

        // Single response: map its action directly, honouring _behaviors (wait/repeat).
        if (responseList.size() == 1) {
            return buildSingleResponseExpectation(httpRequest, responseList.get(0), id, locator, warnings);
        }

        // Multiple responses. Mountebank cycles through them. If they are all `is` responses we can
        // map to one sequential multi-response expectation; otherwise fall back to the first.
        boolean allIs = responseList.stream().allMatch(r -> r.has("is"));
        if (allIs) {
            List<HttpResponse> httpResponses = new ArrayList<>();
            boolean repeatSeen = false;
            for (JsonNode r : responseList) {
                HttpResponse hr = mapIsResponse(r.path("is"), locator, warnings);
                applyWait(hr, r, locator, warnings);
                if (r.path("_behaviors").path("repeat").isInt()) {
                    repeatSeen = true;
                }
                warnUnmappedBehaviors(r, locator, warnings);
                httpResponses.add(hr);
            }
            if (repeatSeen) {
                warnings.add(new ImportWarning(locator, "responses._behaviors.repeat",
                    "per-response 'repeat' is not supported for a cycling multi-response expectation — each response is served once per cycle"));
            }
            return new Expectation(httpRequest).withId(id)
                .thenRespond(httpResponses)
                .withResponseMode(org.mockserver.mock.ResponseMode.SEQUENTIAL);
        }

        warnings.add(new ImportWarning(locator, "stub.responses",
            "stub has multiple responses of mixed/non-'is' type — only the first response was mapped (MockServer binds one action per expectation)"));
        return buildSingleResponseExpectation(httpRequest, responseList.get(0), id, locator, warnings);
    }

    private Expectation buildSingleResponseExpectation(HttpRequest httpRequest, JsonNode responseNode, String id, String locator, List<ImportWarning> warnings) {
        warnUnmappedBehaviors(responseNode, locator, warnings);

        // In Mountebank a single response wraps and is served indefinitely; the per-response
        // 'repeat' count only governs how many times each response is used while cycling through a
        // multi-response list. Mapping it to Times.exactly(N) here would make the stub 404 after N
        // matching requests, silently diverging from Mountebank — so keep the expectation unlimited
        // and warn that the repeat count was dropped (consistent with the multi-response path).
        JsonNode repeat = responseNode.path("_behaviors").path("repeat");
        if (repeat.isInt() && repeat.asInt() > 0) {
            warnings.add(new ImportWarning(locator, "response._behaviors.repeat",
                "'repeat' is dropped for a single-response stub — Mountebank serves a single response indefinitely, so the response stays available for every matching request"));
        }
        Expectation expectation = new Expectation(httpRequest).withId(id);

        if (responseNode.has("is")) {
            HttpResponse hr = mapIsResponse(responseNode.path("is"), locator, warnings);
            applyWait(hr, responseNode, locator, warnings);
            expectation.thenRespond(hr);
        } else if (responseNode.has("proxy")) {
            expectation.thenForward(mapProxy(responseNode.path("proxy"), locator, warnings));
        } else if (responseNode.has("fault")) {
            expectation.thenError(mapFault(responseNode.path("fault").asText(), locator, warnings));
        } else if (responseNode.has("inject")) {
            warnings.add(new ImportWarning(locator, "response.inject",
                "JavaScript inject responses are not mapped — a placeholder 501 response was generated"));
            expectation.thenRespond(response().withStatusCode(501).withBody("inject response not imported"));
        } else {
            warnings.add(new ImportWarning(locator, "response",
                "unrecognised response type " + fieldNames(responseNode) + " — a default 200 response was generated"));
            expectation.thenRespond(response().withStatusCode(200));
        }
        return expectation;
    }

    private void applyPredicate(HttpRequest httpRequest, JsonNode predicate, String locator, List<ImportWarning> warnings) {
        // Compound predicates have no flat MockServer equivalent.
        if (predicate.has("and") || predicate.has("or") || predicate.has("not")) {
            warnings.add(new ImportWarning(locator, "predicate." + fieldNames(predicate),
                "compound predicates (and/or/not) are not mapped — omitted"));
            return;
        }
        if (predicate.has("inject")) {
            warnings.add(new ImportWarning(locator, "predicate.inject",
                "JavaScript inject predicates are not mapped — omitted"));
            return;
        }

        String operator = firstFieldName(predicate);
        if (operator == null) {
            return;
        }
        JsonNode fields = predicate.path(operator);
        if (!fields.isObject()) {
            warnings.add(new ImportWarning(locator, "predicate." + operator, "unsupported predicate shape — omitted"));
            return;
        }
        if (predicate.has("except")) {
            warnings.add(new ImportWarning(locator, "predicate." + operator,
                "'except' predicate modifier is not mapped — matching uses MockServer defaults"));
        }
        // Mountebank predicates are case-insensitive by default (caseSensitive:false). Regex-mapped
        // operators are made case-insensitive faithfully with a (?i) prefix (see toMatcher /
        // applyBodyPredicate); exact-match operators (equals/deepEquals) have no case-insensitive
        // MockServer analogue, so warn that they will match case-sensitively rather than silently
        // diverging from the source.
        boolean caseInsensitive = !predicate.path("caseSensitive").asBoolean(false);
        if (caseInsensitive && ("equals".equals(operator) || "deepEquals".equals(operator))) {
            warnings.add(new ImportWarning(locator, "predicate." + operator,
                "Mountebank matches case-insensitively by default but MockServer matches these exact values case-sensitively — set caseSensitive:true upstream or adjust the values if case-insensitive matching is required"));
        }

        for (Iterator<Map.Entry<String, JsonNode>> it = fields.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> field = it.next();
            String requestField = field.getKey();
            JsonNode value = field.getValue();
            switch (requestField.toLowerCase(Locale.ROOT)) {
                case "method":
                    httpRequest.withMethod(toMatcher(operator, value.asText(), locator, "method", warnings, caseInsensitive));
                    break;
                case "path":
                    httpRequest.withPath(toMatcher(operator, value.asText(), locator, "path", warnings, caseInsensitive));
                    break;
                case "query":
                    applyMapPredicate(value, operator, locator, "query", warnings, httpRequest, true, caseInsensitive);
                    break;
                case "headers":
                    applyMapPredicate(value, operator, locator, "headers", warnings, httpRequest, false, caseInsensitive);
                    break;
                case "body":
                    applyBodyPredicate(httpRequest, operator, value, locator, warnings, caseInsensitive);
                    break;
                default:
                    warnings.add(new ImportWarning(locator, "predicate." + operator + "." + requestField,
                        "request field '" + requestField + "' is not mapped — omitted"));
            }
        }
    }

    private void applyMapPredicate(JsonNode map, String operator, String locator, String field,
                                   List<ImportWarning> warnings, HttpRequest httpRequest, boolean query, boolean caseInsensitive) {
        if (!map.isObject()) {
            return;
        }
        List<Parameter> params = new ArrayList<>();
        List<Header> headers = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = map.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String key = entry.getKey();
            JsonNode v = entry.getValue();
            if ("exists".equals(operator)) {
                boolean mustExist = v.asBoolean(true);
                if (mustExist) {
                    if (query) {
                        params.add(Parameter.param(NottableString.string(key), NottableString.string(".*")));
                    } else {
                        headers.add(Header.header(NottableString.string(key), NottableString.string(".*")));
                    }
                } else {
                    warnings.add(new ImportWarning(locator, "predicate.exists." + field + "." + key,
                        "'exists:false' (must-be-absent) has no direct MockServer analogue on a named key — omitted"));
                }
                continue;
            }
            NottableString matcher = toMatcher(operator, v.asText(), locator, field + "." + key, warnings, caseInsensitive);
            if (query) {
                params.add(Parameter.param(NottableString.string(key), matcher));
            } else {
                headers.add(Header.header(NottableString.string(key), matcher));
            }
        }
        if (!params.isEmpty()) {
            for (Parameter p : params) {
                httpRequest.withQueryStringParameter(p);
            }
        }
        if (!headers.isEmpty()) {
            for (Header h : headers) {
                httpRequest.withHeader(h);
            }
        }
    }

    private void applyBodyPredicate(HttpRequest httpRequest, String operator, JsonNode value, String locator, List<ImportWarning> warnings, boolean caseInsensitive) {
        // Body can be a JSON object/array (structural match) or a scalar string.
        if (value.isObject() || value.isArray()) {
            MatchType matchType = "deepEquals".equals(operator) ? MatchType.STRICT : MatchType.ONLY_MATCHING_FIELDS;
            httpRequest.withBody(JsonBody.json(value.toString(), matchType));
            return;
        }
        String text = value.asText();
        // Mountebank's default case-insensitivity is preserved with a (?i) prefix wherever the body
        // maps to a regex matcher; exact-match operators are covered by the predicate-level warning.
        String ci = caseInsensitive ? "(?i)" : "";
        switch (operator) {
            case "equals":
            case "deepEquals":
                httpRequest.withBody(StringBody.exact(text));
                break;
            case "contains":
                // A plain substring body match is case-sensitive; when the source is case-insensitive
                // map to a case-insensitive regex substring instead so the divergence is not silent.
                if (caseInsensitive) {
                    httpRequest.withBody(org.mockserver.model.RegexBody.regex("(?i).*" + java.util.regex.Pattern.quote(text) + ".*"));
                } else {
                    httpRequest.withBody(StringBody.subString(text));
                }
                break;
            case "matches":
                httpRequest.withBody(org.mockserver.model.RegexBody.regex(ci + text));
                break;
            case "startsWith":
                httpRequest.withBody(org.mockserver.model.RegexBody.regex(ci + "^" + java.util.regex.Pattern.quote(text) + ".*"));
                break;
            case "endsWith":
                httpRequest.withBody(org.mockserver.model.RegexBody.regex(ci + ".*" + java.util.regex.Pattern.quote(text) + "$"));
                break;
            default:
                warnings.add(new ImportWarning(locator, "predicate." + operator + ".body",
                    "body predicate operator '" + operator + "' is not mapped — omitted"));
        }
    }

    private NottableString toMatcher(String operator, String value, String locator, String field, List<ImportWarning> warnings, boolean caseInsensitive) {
        // Mountebank matches case-insensitively by default; for regex-mapped operators preserve that
        // with a (?i) prefix. equals/deepEquals stay exact (the predicate-level warning documents the
        // resulting case-sensitive match).
        String ci = caseInsensitive ? "(?i)" : "";
        switch (operator) {
            case "equals":
            case "deepEquals":
                return NottableString.string(value);
            case "matches":
                return NottableString.string(ci + value);
            case "contains":
                return NottableString.string(ci + ".*" + java.util.regex.Pattern.quote(value) + ".*");
            case "startsWith":
                return NottableString.string(ci + "^" + java.util.regex.Pattern.quote(value) + ".*");
            case "endsWith":
                return NottableString.string(ci + ".*" + java.util.regex.Pattern.quote(value) + "$");
            default:
                warnings.add(new ImportWarning(locator, "predicate." + operator + "." + field,
                    "predicate operator '" + operator + "' is not mapped for " + field + " — treated as exact match"));
                return NottableString.string(value);
        }
    }

    private HttpResponse mapIsResponse(JsonNode is, String locator, List<ImportWarning> warnings) {
        HttpResponse httpResponse = response();
        httpResponse.withStatusCode(is.path("statusCode").asInt(200));

        JsonNode headers = is.path("headers");
        if (headers.isObject()) {
            List<Header> headerList = new ArrayList<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = headers.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                JsonNode v = field.getValue();
                if (v.isArray()) {
                    List<String> values = new ArrayList<>();
                    v.forEach(n -> values.add(n.asText()));
                    headerList.add(Header.header(field.getKey(), values));
                } else {
                    headerList.add(Header.header(field.getKey(), v.asText()));
                }
            }
            if (!headerList.isEmpty()) {
                httpResponse.withHeaders(headerList);
            }
        }

        JsonNode body = is.path("body");
        if (!body.isMissingNode() && !body.isNull()) {
            if (body.isObject() || body.isArray()) {
                httpResponse.withBody(body.toString(), org.mockserver.model.MediaType.APPLICATION_JSON);
            } else {
                String text = body.asText();
                if (!text.isEmpty()) {
                    httpResponse.withBody(text);
                }
            }
        }

        if (is.has("_mode") && "binary".equalsIgnoreCase(is.path("_mode").asText())) {
            warnings.add(new ImportWarning(locator, "response.is._mode",
                "binary response mode is not mapped; the body is treated as text"));
        }
        return httpResponse;
    }

    private HttpForward mapProxy(JsonNode proxy, String locator, List<ImportWarning> warnings) {
        String to = textOrNull(proxy, "to");
        HttpForward forward = HttpForward.forward();
        if (to != null) {
            try {
                URI uri = new URI(to);
                forward.withHost(uri.getHost());
                boolean https = "https".equalsIgnoreCase(uri.getScheme());
                forward.withScheme(https ? HttpForward.Scheme.HTTPS : HttpForward.Scheme.HTTP);
                int port = uri.getPort();
                forward.withPort(port > 0 ? port : (https ? 443 : 80));
            } catch (Exception e) {
                warnings.add(new ImportWarning(locator, "response.proxy.to",
                    "could not parse proxy target '" + to + "' — forward may be incomplete"));
            }
        }
        String mode = textOrNull(proxy, "mode");
        if (mode != null && !"proxyTransparent".equalsIgnoreCase(mode)) {
            warnings.add(new ImportWarning(locator, "response.proxy.mode",
                "proxy mode '" + mode + "' (record/replay) is not reproduced; MockServer forwards every matching request"));
        }
        if (proxy.has("predicateGenerators")) {
            warnings.add(new ImportWarning(locator, "response.proxy.predicateGenerators",
                "proxy predicateGenerators (record-and-replay stub generation) are not mapped"));
        }
        return forward;
    }

    private HttpError mapFault(String fault, String locator, List<ImportWarning> warnings) {
        if ("CONNECTION_RESET_BY_PEER".equalsIgnoreCase(fault)) {
            return HttpError.error().withResetConnection(true);
        }
        if ("RANDOM_DATA_THEN_CLOSE".equalsIgnoreCase(fault)) {
            warnings.add(new ImportWarning(locator, "response.fault.RANDOM_DATA_THEN_CLOSE",
                "approximated as an immediate connection drop; the random bytes are not reproduced"));
        } else {
            warnings.add(new ImportWarning(locator, "response.fault." + fault,
                "unknown fault type — approximated as a dropped connection"));
        }
        return HttpError.error().withDropConnection(true);
    }

    private void applyWait(HttpResponse httpResponse, JsonNode responseNode, String locator, List<ImportWarning> warnings) {
        JsonNode wait = responseNode.path("_behaviors").path("wait");
        if (wait.isInt() || wait.isLong()) {
            httpResponse.withDelay(TimeUnit.MILLISECONDS, wait.asLong());
        } else if (wait.isTextual()) {
            warnings.add(new ImportWarning(locator, "response._behaviors.wait",
                "a function-valued 'wait' (JavaScript) is not mapped; no delay applied"));
        }
    }

    private void warnUnmappedBehaviors(JsonNode responseNode, String locator, List<ImportWarning> warnings) {
        JsonNode behaviors = responseNode.path("_behaviors");
        if (!behaviors.isObject()) {
            return;
        }
        for (String key : new String[]{"decorate", "copy", "lookup", "shellTransform"}) {
            if (behaviors.has(key)) {
                warnings.add(new ImportWarning(locator, "response._behaviors." + key,
                    "'" + key + "' post-processing behaviour is not mapped — omitted"));
            }
        }
    }

    private static String firstFieldName(JsonNode node) {
        Iterator<String> names = node.fieldNames();
        // skip predicate modifier keys to find the operator
        while (names.hasNext()) {
            String name = names.next();
            if (!name.equals("caseSensitive") && !name.equals("except")) {
                return name;
            }
        }
        return null;
    }

    private static String fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names.toString();
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        if (child.isMissingNode() || child.isNull() || !child.isValueNode()) {
            return null;
        }
        return child.asText();
    }
}
