package org.mockserver.imports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.mockserver.fixture.FixtureRedactor;
import org.mockserver.mock.Expectation;
import org.mockserver.model.AfterAction;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.Body;
import org.mockserver.model.ConnectionOptions;
import org.mockserver.model.Delay;
import org.mockserver.model.DelayDistribution;
import org.mockserver.model.FileBody;
import org.mockserver.model.Header;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;
import org.mockserver.model.JsonBody;
import org.mockserver.model.JsonPathBody;
import org.mockserver.model.MediaType;
import org.mockserver.model.NottableDateTimeString;
import org.mockserver.model.NottableString;
import org.mockserver.model.Parameter;
import org.mockserver.model.RegexBody;
import org.mockserver.model.StringBody;
import org.mockserver.model.XPathBody;
import org.mockserver.model.XmlBody;
import org.mockserver.matchers.DateTimeComparisonMatcher;
import org.mockserver.matchers.MatchType;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.HttpResponseDTO;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Imports MockServer expectations from <a href="https://wiremock.org/">WireMock</a> stub-mapping
 * JSON.
 *
 * <p>Accepts three shapes, matching WireMock's own on-disk / API formats:
 * <ul>
 *     <li>a single stub-mapping object ({@code { "request": {...}, "response": {...} }});</li>
 *     <li>a {@code mappings} wrapper ({@code { "mappings": [ {stub}, ... ] }}) — the
 *         {@code GET /__admin/mappings} export and {@code mappings/} directory format;</li>
 *     <li>a bare JSON array of stub-mappings.</li>
 * </ul>
 *
 * <h2>Mapping</h2>
 * <table>
 *   <tr><th>WireMock request</th><th>MockServer matcher</th></tr>
 *   <tr><td>{@code method}</td><td>method (dropped when {@code ANY})</td></tr>
 *   <tr><td>{@code urlPath}</td><td>exact path</td></tr>
 *   <tr><td>{@code urlPathPattern} / {@code urlPattern}</td><td>regex path</td></tr>
 *   <tr><td>{@code url}</td><td>exact path + query parameters</td></tr>
 *   <tr><td>{@code queryParameters}, {@code headers}</td><td>parameter / header matchers
 *       ({@code equalTo} exact, {@code matches} regex, {@code contains} regex, {@code absent},
 *       {@code before} / {@code after} / {@code equalToDateTime} date-time matchers)</td></tr>
 *   <tr><td>{@code bodyPatterns}</td><td>body matcher ({@code equalToJson}, {@code matchesJsonPath},
 *       {@code matchesXPath}, either with a {@code before} / {@code after} / {@code equalToDateTime}
 *       result matcher, {@code contains}, {@code matches}/{@code equalTo})</td></tr>
 * </table>
 *
 * <p>The {@code response} maps to an {@link HttpResponse} (status/headers/body/{@code base64Body}/
 * {@code jsonBody}; {@code fixedDelayMilliseconds} and {@code delayDistribution} → delay;
 * {@code chunkedDribbleDelay} → chunked {@link ConnectionOptions}; {@code bodyFileName} → {@link FileBody}
 * when a files directory is supplied). A {@code response-template} transformer whose template only uses
 * simple request references maps to a Mustache {@link HttpTemplate}. {@code fault} maps to an
 * {@link HttpError}; a {@code proxyBaseUrl} response maps to an {@link HttpForward}; webhooks
 * ({@code postServeActions} / {@code serveEventListeners}) map to after-actions. WireMock
 * scenarios ({@code scenarioName}/{@code requiredScenarioState}/{@code newScenarioState}) map
 * directly onto MockServer scenarios, and {@code priority} maps to MockServer priority {@code 5 - p}
 * (WireMock 1 = highest and an unspecified WireMock priority defaults to 5, so the mapping keeps an
 * explicit priority sitting relative to MockServer's unspecified default of 0 the same way it sits
 * relative to WireMock's default of 5).
 *
 * <p>Constructs with no faithful MockServer equivalent (e.g. Handlebars helpers, custom transformers,
 * {@code metadata}) are reported as {@link ImportWarning}s rather than silently dropped.
 */
public class WireMockImporter {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();
    private static final String[] DATE_TIME_PREDICATES = {"before", "after", "equalToDateTime"};
    // WireMock's lognormal is parameterised by sigma; MockServer's by its 99th percentile
    private static final double Z_SCORE_P99 = 2.326347874;
    // the bytes WireMock writes for MALFORMED_RESPONSE_CHUNK and RANDOM_DATA_THEN_CLOSE
    private static final byte[] WIREMOCK_GARBAGE = "lskdu018973t09sylgasjkfg1][]'./.sdlv".getBytes(StandardCharsets.UTF_8);
    private static final Pattern PATH_TEMPLATE_NAME = Pattern.compile("\\{([^/{}]+)}");
    private static final Pattern URL_PARTS = Pattern.compile("^(https?)://([^/?#]+)([^?#]*)(?:\\?([^#]*))?.*$", Pattern.CASE_INSENSITIVE);

    public ImportResult importExpectations(String wireMockJson) {
        return importExpectations(wireMockJson, ImportRedaction.Options.enabled());
    }

    public ImportResult importExpectations(String wireMockJson, ImportRedaction.Options redactionOptions) {
        return importExpectations(wireMockJson, redactionOptions, null);
    }

    /**
     * @param filesDirectory the WireMock {@code __files} directory on the MockServer host, used to resolve
     *                       {@code bodyFileName}; when null, a {@code bodyFileName} is reported as a warning
     */
    public ImportResult importExpectations(String wireMockJson, ImportRedaction.Options redactionOptions, String filesDirectory) {
        Path filesRoot = filesDirectory == null || filesDirectory.isBlank() ? null : Paths.get(filesDirectory).toAbsolutePath().normalize();
        if (wireMockJson == null || wireMockJson.trim().isEmpty()) {
            throw new IllegalArgumentException("WireMock stub JSON body is required");
        }

        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(wireMockJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to parse WireMock stub JSON: " + e.getMessage(), e);
        }

        List<JsonNode> stubs = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(stubs::add);
        } else if (root.has("mappings") && root.path("mappings").isArray()) {
            root.path("mappings").forEach(stubs::add);
        } else if (root.has("request") || root.has("response")) {
            stubs.add(root);
        } else {
            throw new IllegalArgumentException(
                "not a valid WireMock stub document — expected a stub object with 'request'/'response', a 'mappings' array, or a JSON array of stubs");
        }

        List<Expectation> expectations = new ArrayList<>();
        List<String> locators = new ArrayList<>();
        List<ImportWarning> warnings = new ArrayList<>();
        int index = 0;
        for (JsonNode stub : stubs) {
            String locator = "stub[" + index + "]";
            Expectation expectation = buildExpectation(stub, index, locator, filesRoot, redactionOptions, warnings);
            if (expectation != null) {
                expectations.add(expectation);
                locators.add(locator);
            }
            index++;
        }

        return new ImportResult(ImportRedaction.redactPreservingActions(expectations, redactionOptions, locators, warnings), warnings);
    }

    private Expectation buildExpectation(JsonNode stub, int index, String locator, Path filesRoot,
                                         ImportRedaction.Options redactionOptions, List<ImportWarning> warnings) {
        JsonNode requestNode = stub.path("request");
        JsonNode responseNode = stub.path("response");
        if (requestNode.isMissingNode() && responseNode.isMissingNode()) {
            warnings.add(new ImportWarning(locator, "stub", "skipped — neither 'request' nor 'response' present"));
            return null;
        }

        HttpRequest httpRequest = buildRequest(requestNode, locator, redactionOptions, warnings);

        Expectation expectation = new Expectation(httpRequest).withId("wiremock-" + index + idSuffix(stub));

        // Scenario mapping — WireMock scenarios line up 1:1 with MockServer scenarios
        // ("Started" is the default start state in both).
        String scenarioName = textOrNull(stub, "scenarioName");
        if (scenarioName != null) {
            expectation.withScenarioName(scenarioName);
            String requiredState = textOrNull(stub, "requiredScenarioState");
            if (requiredState != null) {
                expectation.withScenarioState(requiredState);
            }
            String newState = textOrNull(stub, "newScenarioState");
            if (newState != null) {
                expectation.withNewScenarioState(newState);
            }
        }

        // Priority — WireMock 1 = highest precedence and an unspecified WireMock priority defaults
        // to 5; MockServer matches higher numbers first with an unspecified default of 0. Map
        // WireMock priority p to MockServer (5 - p) so an explicit priority ranks against
        // MockServer's default of 0 the way it ranked against WireMock's default of 5:
        // WM 1 -> MS 4 (beats an unspecified catch-all at 0), WM 10 -> MS -5 (loses to it).
        // A plain `-priority` inversion instead put WM 1 at MS -1, losing to that catch-all.
        JsonNode priorityNode = stub.path("priority");
        if (priorityNode.isInt()) {
            expectation.withPriority(5 - priorityNode.asInt());
        }

        if (stub.has("metadata")) {
            warnings.add(new ImportWarning(locator, "metadata",
                "stub metadata is not mapped; MockServer expectations carry no metadata"));
        }
        if (stub.path("persistent").asBoolean(false)) {
            warnings.add(new ImportWarning(locator, "persistent",
                "the per-stub persistent flag is not mapped; enable the persistExpectations configuration property to persist all expectations"));
        }

        applyResponse(expectation, responseNode, pathContext(requestNode), locator, filesRoot, redactionOptions, warnings);
        applyWebhooks(expectation, stub, locator, warnings);
        return expectation;
    }

    private static WireMockTemplateTranslator.PathContext pathContext(JsonNode requestNode) {
        String exactPath = textOrNull(requestNode, "urlPath");
        String url = textOrNull(requestNode, "url");
        boolean otherPathMatcher = requestNode.has("urlPathTemplate") || requestNode.has("urlPathPattern") || requestNode.has("urlPattern");
        if (exactPath == null && url != null && !otherPathMatcher) {
            int q = url.indexOf('?');
            exactPath = q >= 0 ? url.substring(0, q) : url;
        }
        return new WireMockTemplateTranslator.PathContext(exactPath, textOrNull(requestNode, "urlPathTemplate") != null);
    }

    private HttpRequest buildRequest(JsonNode requestNode, String locator, ImportRedaction.Options redactionOptions, List<ImportWarning> warnings) {
        HttpRequest httpRequest = request();
        if (requestNode.isMissingNode()) {
            return httpRequest;
        }

        String method = textOrNull(requestNode, "method");
        if (method != null && !"ANY".equalsIgnoreCase(method)) {
            httpRequest.withMethod(method.toUpperCase(Locale.ROOT));
        }

        // Path resolution — precedence mirrors WireMock's own (urlPath, urlPathPattern,
        // urlPattern, url). Only one is normally present.
        String urlPath = textOrNull(requestNode, "urlPath");
        String urlPathTemplate = textOrNull(requestNode, "urlPathTemplate");
        String urlPathPattern = textOrNull(requestNode, "urlPathPattern");
        String urlPattern = textOrNull(requestNode, "urlPattern");
        String url = textOrNull(requestNode, "url");
        List<Parameter> queryParams = new ArrayList<>();

        if (urlPath != null) {
            httpRequest.withPath(urlPath);
        } else if (urlPathTemplate != null) {
            // MockServer path templates use the same {name} syntax, but only a name declared as a path parameter
            // matches a segment, so a name WireMock leaves unconstrained is declared as any one segment
            httpRequest.withPath(urlPathTemplate);
            Set<String> declared = new HashSet<>();
            JsonNode pathParameters = requestNode.path("pathParameters");
            if (pathParameters.isObject()) {
                for (Iterator<Map.Entry<String, JsonNode>> it = pathParameters.fields(); it.hasNext(); ) {
                    Map.Entry<String, JsonNode> field = it.next();
                    NottableString value = matchPredicate(field.getValue(), locator, "pathParameters." + field.getKey(), warnings);
                    if (value != null) {
                        httpRequest.withPathParameter(Parameter.param(NottableString.string(field.getKey()), value));
                        declared.add(field.getKey());
                    }
                }
            }
            Matcher names = PATH_TEMPLATE_NAME.matcher(urlPathTemplate);
            while (names.find()) {
                if (declared.add(names.group(1))) {
                    httpRequest.withPathParameter(names.group(1), "[^/]+");
                }
            }
        } else if (urlPathPattern != null) {
            httpRequest.withPath(urlPathPattern);
        } else if (urlPattern != null) {
            // urlPattern matches path AND query as one regex; MockServer matches the path only,
            // so strip a trailing query fragment and warn if one was present.
            int q = urlPattern.indexOf("\\?");
            if (q < 0) {
                q = urlPattern.indexOf('?');
            }
            if (q >= 0) {
                warnings.add(new ImportWarning(locator, "request.urlPattern",
                    "regex covered the query string; MockServer matches the path regex only — query portion dropped"));
                httpRequest.withPath(urlPattern.substring(0, q));
            } else {
                httpRequest.withPath(urlPattern);
            }
        } else if (url != null) {
            try {
                URI uri = new URI(url);
                String path = uri.getPath();
                httpRequest.withPath(path == null || path.isEmpty() ? "/" : path);
                queryParams.addAll(parseQueryString(uri.getRawQuery()));
            } catch (Exception e) {
                httpRequest.withPath(url.startsWith("/") ? url : "/");
            }
        }

        // queryParameters
        JsonNode queryParameters = requestNode.path("queryParameters");
        if (queryParameters.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = queryParameters.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                NottableString value = matchPredicate(field.getValue(), locator, "queryParameters." + field.getKey(), warnings);
                if (value != null) {
                    queryParams.add(Parameter.param(NottableString.string(field.getKey()), value));
                }
            }
        }
        if (!queryParams.isEmpty()) {
            httpRequest.withQueryStringParameters(queryParams);
        }

        // headers
        JsonNode headers = requestNode.path("headers");
        if (headers.isObject()) {
            List<Header> headerList = new ArrayList<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = headers.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                NottableString value = matchPredicate(field.getValue(), locator, "headers." + field.getKey(), warnings);
                if (value != null) {
                    headerList.add(Header.header(NottableString.string(field.getKey()), value));
                }
            }
            if (!headerList.isEmpty()) {
                httpRequest.withHeaders(headerList);
            }
        }

        // cookies
        JsonNode cookies = requestNode.path("cookies");
        if (cookies.isObject() && !cookies.isEmpty()) {
            warnings.add(new ImportWarning(locator, "request.cookies",
                "cookie predicates are not mapped; add explicit cookie matchers if needed"));
        }

        // bodyPatterns
        JsonNode bodyPatterns = requestNode.path("bodyPatterns");
        if (bodyPatterns.isArray() && !bodyPatterns.isEmpty()) {
            applyBodyPattern(httpRequest, bodyPatterns, locator, redactionOptions, warnings);
        }

        // basicAuthCredentials / multipartPatterns — not mapped
        if (requestNode.has("basicAuthCredentials")) {
            warnings.add(new ImportWarning(locator, "request.basicAuthCredentials",
                "basic-auth credential matching is not mapped; add an Authorization header matcher if needed"));
        }
        if (requestNode.has("multipartPatterns")) {
            warnings.add(new ImportWarning(locator, "request.multipartPatterns",
                "multipart body-part matching is not mapped"));
        }

        return httpRequest;
    }

    /**
     * Convert a WireMock match predicate object ({@code {"equalTo":"x"}}, {@code {"matches":"re"}},
     * {@code {"contains":"y"}}, {@code {"absent":true}}) into a {@link NottableString} suitable for a
     * header / query-parameter matcher. Returns {@code null} when the whole entry should be omitted
     * (an {@code absent} predicate has no MockServer analogue on a single named key).
     */
    private NottableString matchPredicate(JsonNode predicate, String locator, String field, List<ImportWarning> warnings) {
        if (predicate.isTextual()) {
            return NottableString.string(predicate.asText());
        }
        if (!predicate.isObject()) {
            return NottableString.string(predicate.asText());
        }
        if (predicate.has("equalTo")) {
            boolean caseInsensitive = predicate.path("caseInsensitive").asBoolean(false);
            String value = predicate.path("equalTo").asText();
            if (caseInsensitive) {
                return NottableString.string("(?i)" + java.util.regex.Pattern.quote(value));
            }
            return NottableString.string(value);
        }
        if (predicate.has("matches")) {
            return NottableString.string(predicate.path("matches").asText());
        }
        if (predicate.has("contains")) {
            return NottableString.string(".*" + java.util.regex.Pattern.quote(predicate.path("contains").asText()) + ".*");
        }
        if (predicate.has("doesNotMatch")) {
            return NottableString.not(predicate.path("doesNotMatch").asText());
        }
        for (String operator : DATE_TIME_PREDICATES) {
            if (predicate.has(operator)) {
                return dateTimePredicate(operator, predicate, locator, field, warnings);
            }
        }
        if (predicate.path("absent").asBoolean(false)) {
            warnings.add(new ImportWarning(locator, field,
                "'absent' predicate has no direct MockServer analogue on a named key — omitted (a request lacking the key already matches)"));
            return null;
        }
        warnings.add(new ImportWarning(locator, field,
            "unsupported match predicate " + fieldNames(predicate) + " — omitted"));
        return null;
    }

    /**
     * Maps WireMock's {@code before} / {@code after} / {@code equalToDateTime} onto a MockServer date-time
     * matcher; {@code expectedOffset} with {@code expectedOffsetUnit} folds into a {@code now} offset.
     */
    private NottableString dateTimePredicate(String operator, JsonNode predicate, String locator, String field, List<ImportWarning> warnings) {
        String expected = predicate.path(operator).asText().trim();
        if (predicate.has("expectedOffset")) {
            String offset = predicate.path("expectedOffset").asText() + " " + predicate.path("expectedOffsetUnit").asText("days").toLowerCase(Locale.ROOT);
            if (expected.regionMatches(true, 0, "now", 0, 3)) {
                expected = expected + " " + (offset.startsWith("-") ? offset : "+" + offset);
            } else {
                warnings.add(new ImportWarning(locator, field, "expectedOffset on a literal date-time is not mapped — the literal is used as given"));
            }
        }
        try {
            return NottableDateTimeString.dateTime(
                DateTimeComparisonMatcher.Operator.forFieldName(operator),
                expected,
                predicate.hasNonNull("actualFormat") ? predicate.path("actualFormat").asText() : null,
                predicate.hasNonNull("truncateExpected") ? predicate.path("truncateExpected").asText() : null,
                predicate.hasNonNull("truncateActual") ? predicate.path("truncateActual").asText() : null,
                predicate.has("applyTruncationLast") ? predicate.path("applyTruncationLast").asBoolean(false) : null,
                null,
                false
            );
        } catch (IllegalArgumentException e) {
            warnings.add(new ImportWarning(locator, field, "'" + operator + "' predicate could not be mapped (" + e.getMessage() + ") — omitted"));
            return null;
        }
    }

    private static String dateTimeOperator(JsonNode predicate) {
        for (String operator : DATE_TIME_PREDICATES) {
            if (predicate.has(operator)) {
                return operator;
            }
        }
        return null;
    }

    private void applyBodyPattern(HttpRequest httpRequest, JsonNode bodyPatterns, String locator, ImportRedaction.Options redactionOptions, List<ImportWarning> warnings) {
        // MockServer matches a single Body per request: apply the first mappable pattern, warn on the rest
        boolean bodySet = false;
        int i = 0;
        for (JsonNode pattern : bodyPatterns) {
            String patternLocator = locator + " bodyPatterns[" + i + "]";
            i++;
            Body<?> body = bodyMatcher(pattern, patternLocator, redactionOptions, warnings);
            if (body == null) {
                continue;
            }
            if (bodySet) {
                warnings.add(new ImportWarning(patternLocator, "bodyPatterns",
                    "multiple body patterns; only the first mappable one is applied"));
                continue;
            }
            httpRequest.withBody(body);
            bodySet = true;
        }
    }

    /**
     * Maps one WireMock body pattern to a MockServer body matcher, or returns null (with a warning) when
     * it has no equivalent.
     */
    private Body<?> bodyMatcher(JsonNode pattern, String patternLocator, ImportRedaction.Options redactionOptions, List<ImportWarning> warnings) {
        if (pattern.has("equalToJson")) {
            boolean ignoreExtra = pattern.path("ignoreExtraElements").asBoolean(false);
            MatchType matchType = ignoreExtra ? MatchType.ONLY_MATCHING_FIELDS : MatchType.STRICT;
            JsonNode json = pattern.path("equalToJson");
            if (pattern.path("ignoreArrayOrder").asBoolean(false) && !ignoreExtra) {
                warnings.add(new ImportWarning(patternLocator, "bodyPatterns.ignoreArrayOrder",
                    "ignoreArrayOrder is approximated by MockServer's JSON matching and not separately configurable"));
            }
            return JsonBody.json(json.isTextual() ? json.asText() : json.toString(), matchType);
        }
        if (pattern.has("matchesJsonPath")) {
            JsonNode jsonPath = pattern.path("matchesJsonPath");
            String expression = jsonPath.isTextual() ? jsonPath.asText() : textOrNull(jsonPath, "expression");
            if (expression == null) {
                warnings.add(new ImportWarning(patternLocator, "bodyPatterns.matchesJsonPath",
                    "matchesJsonPath object form has no 'expression' field — body matcher omitted"));
                return null;
            }
            String dateTimeOperator = jsonPath.isObject() ? dateTimeOperator(jsonPath) : null;
            if (dateTimeOperator != null) {
                NottableString dateTime = dateTimePredicate(dateTimeOperator, jsonPath, patternLocator, "bodyPatterns.matchesJsonPath", warnings);
                return dateTime == null ? null : JsonPathBody.jsonPath(expression, (NottableDateTimeString) dateTime);
            }
            if (jsonPath.isObject() && jsonPath.size() > 1) {
                warnings.add(new ImportWarning(patternLocator, "bodyPatterns.matchesJsonPath",
                    "the sub-matcher on the JSONPath result is not applied; the body matches when the expression selects anything"));
            }
            return JsonPathBody.jsonPath(expression);
        }
        if (pattern.has("equalToXml")) {
            for (String unsupported : List.of("enablePlaceholders", "placeholderOpeningDelimiterRegex", "placeholderClosingDelimiterRegex", "exemptedComparisons")) {
                if (pattern.has(unsupported)) {
                    warnings.add(new ImportWarning(patternLocator, "bodyPatterns.equalToXml." + unsupported,
                        unsupported + " is not mapped; MockServer compares XML for similarity, ignoring whitespace, comments and element order"));
                }
            }
            return XmlBody.xml(pattern.path("equalToXml").asText());
        }
        if (pattern.has("matchesXPath")) {
            return xpathMatcher(pattern, patternLocator, redactionOptions, warnings);
        }
        if (pattern.has("binaryEqualTo")) {
            try {
                return BinaryBody.binary(Base64.getDecoder().decode(pattern.path("binaryEqualTo").asText()));
            } catch (IllegalArgumentException e) {
                warnings.add(new ImportWarning(patternLocator, "bodyPatterns.binaryEqualTo", "invalid Base64 — body matcher omitted"));
                return null;
            }
        }
        if (pattern.has("equalTo")) {
            return StringBody.exact(pattern.path("equalTo").asText());
        }
        if (pattern.has("contains")) {
            return StringBody.subString(pattern.path("contains").asText());
        }
        if (pattern.has("matches")) {
            return RegexBody.regex(pattern.path("matches").asText());
        }
        warnings.add(new ImportWarning(patternLocator, "bodyPatterns",
            "unsupported body pattern " + fieldNames(pattern) + " — omitted"));
        return null;
    }

    /**
     * {@code matchesXPath} is either an expression, or an object holding an expression and a matcher for
     * its result. An {@code equalTo} or {@code contains} result matcher folds into the XPath expression.
     */
    private Body<?> xpathMatcher(JsonNode pattern, String patternLocator, ImportRedaction.Options redactionOptions, List<ImportWarning> warnings) {
        JsonNode xpath = pattern.path("matchesXPath");
        String expression;
        NottableDateTimeString dateTime = null;
        String dateTimeOperator = xpath.isObject() && textOrNull(xpath, "expression") != null ? dateTimeOperator(xpath) : null;
        if (dateTimeOperator != null) {
            expression = xpath.path("expression").asText();
            dateTime = (NottableDateTimeString) dateTimePredicate(dateTimeOperator, xpath, patternLocator, "bodyPatterns.matchesXPath", warnings);
            if (dateTime == null) {
                return null;
            }
        } else if (xpath.isTextual()) {
            expression = xpath.asText();
        } else if (textOrNull(xpath, "expression") != null) {
            String selector = "(" + xpath.path("expression").asText() + ")";
            // a value compared with a sensitive field is masked, as redaction masks that field in a JSON body
            boolean masked = ImportRedaction.mentionsSensitiveBodyField(selector, redactionOptions);
            if (xpath.has("equalTo")) {
                expression = selector + " = " + xpathLiteral(masked ? FixtureRedactor.REDACTED_PLACEHOLDER : xpath.path("equalTo").asText());
            } else if (xpath.has("contains")) {
                expression = selector + "[contains(., " + xpathLiteral(masked ? FixtureRedactor.REDACTED_PLACEHOLDER : xpath.path("contains").asText()) + ")]";
            } else {
                expression = xpath.path("expression").asText();
                if (xpath.size() > 1) {
                    warnings.add(new ImportWarning(patternLocator, "bodyPatterns.matchesXPath",
                        "the result matcher " + fieldNames(xpath) + " is not applied; the body matches when the expression selects anything"));
                }
            }
        } else {
            warnings.add(new ImportWarning(patternLocator, "bodyPatterns.matchesXPath",
                "matchesXPath has no expression — body matcher omitted"));
            return null;
        }
        Map<String, String> namespaces = new LinkedHashMap<>();
        JsonNode namespaceNode = pattern.path("xPathNamespaces");
        if (namespaceNode.isObject()) {
            namespaceNode.fields().forEachRemaining(field -> namespaces.put(field.getKey(), field.getValue().asText()));
        }
        if (dateTime != null) {
            return XPathBody.xpath(expression, namespaces.isEmpty() ? null : namespaces, dateTime);
        }
        return namespaces.isEmpty() ? XPathBody.xpath(expression) : XPathBody.xpath(expression, namespaces);
    }

    private static String xpathLiteral(String value) {
        if (!value.contains("'")) {
            return "'" + value + "'";
        }
        if (!value.contains("\"")) {
            return "\"" + value + "\"";
        }
        return "concat('" + value.replace("'", "', \"'\", '") + "')";
    }

    private void applyResponse(Expectation expectation, JsonNode responseNode, WireMockTemplateTranslator.PathContext pathContext,
                               String locator, Path filesRoot, ImportRedaction.Options redactionOptions, List<ImportWarning> warnings) {
        if (responseNode.isMissingNode()) {
            expectation.thenRespond(response().withStatusCode(200));
            return;
        }

        // fault → HttpError (connection-level failure). Any fault takes precedence over a body.
        String fault = textOrNull(responseNode, "fault");
        if (fault != null) {
            expectation.thenError(mapFault(fault, locator, warnings));
            return;
        }

        Delay delay = mapDelay(responseNode.path("fixedDelayMilliseconds"), responseNode.path("delayDistribution"), locator, "response", warnings);

        String proxyBaseUrl = textOrNull(responseNode, "proxyBaseUrl");
        if (proxyBaseUrl != null) {
            HttpForward forward = mapForward(proxyBaseUrl, locator, warnings);
            if (delay != null) {
                forward.withDelay(delay);
            }
            expectation.thenForward(forward);
            return;
        }

        HttpResponse httpResponse = response();
        httpResponse.withStatusCode(responseNode.path("status").asInt(200));
        List<Header> headers = responseHeaders(responseNode.path("headers"));
        if (!headers.isEmpty()) {
            httpResponse.withHeaders(headers);
        }
        boolean templated = templatingEnabled(responseNode, locator, warnings);

        // Body precedence: jsonBody (structured) > body (string) > base64Body > bodyFileName
        JsonNode jsonBody = responseNode.path("jsonBody");
        String body = textOrNull(responseNode, "body");
        String base64Body = textOrNull(responseNode, "base64Body");
        String bodyFileName = textOrNull(responseNode, "bodyFileName");
        String templateBody = null;
        long bodyLength = -1;
        if (!jsonBody.isMissingNode() && !jsonBody.isNull()) {
            templateBody = jsonBody.toString();
            httpResponse.withBody(templateBody, MediaType.APPLICATION_JSON);
        } else if (body != null && !body.isEmpty()) {
            templateBody = body;
            httpResponse.withBody(body);
        } else if (base64Body != null && !base64Body.isEmpty()) {
            try {
                // Decode to a text body (mirrors HarImporter) so the generated expectation carries
                // readable content; a binary body would re-encode to Base64 on serialization.
                httpResponse.withBody(new String(Base64.getDecoder().decode(base64Body), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                warnings.add(new ImportWarning(locator, "response.base64Body", "invalid Base64 — response body omitted"));
            }
        } else if (bodyFileName != null) {
            Path file = resolveBodyFile(bodyFileName, filesRoot, locator, warnings);
            if (file != null) {
                templateBody = templated ? readTemplatedFile(file) : null;
                if (templateBody != null) {
                    httpResponse.withBody(templateBody);
                } else {
                    String contentType = httpResponse.getFirstHeader("Content-Type");
                    httpResponse.withBody(FileBody.file(file.toString(), contentType == null || contentType.isEmpty() ? null : MediaType.parse(contentType)));
                    bodyLength = fileSize(file);
                }
            }
        }
        if (bodyLength < 0 && httpResponse.getBody() != null) {
            bodyLength = httpResponse.getBody().getRawBytes().length;
        }

        boolean templateBodyHasTags = templated && WireMockTemplateTranslator.hasTags(templateBody);
        ConnectionOptions dribble = mapChunkedDribble(responseNode.path("chunkedDribbleDelay"), bodyLength, templateBodyHasTags, locator, warnings);
        if (dribble != null) {
            httpResponse.withConnectionOptions(dribble);
        }
        HttpTemplate template = templated ? responseTemplate(httpResponse, templateBody, pathContext, locator, redactionOptions, warnings) : null;
        if (template != null) {
            if (delay != null) {
                template.withDelay(delay);
            }
            expectation.thenRespond(template);
            return;
        }
        if (delay != null) {
            httpResponse.withDelay(delay);
        }
        expectation.thenRespond(httpResponse);
    }

    private static List<Header> responseHeaders(JsonNode headers) {
        List<Header> headerList = new ArrayList<>();
        if (headers.isObject()) {
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
        }
        return headerList;
    }

    /**
     * True when the stub asks for WireMock's {@code response-template} transformer. Any other transformer
     * is a WireMock extension with no MockServer equivalent.
     */
    private static boolean templatingEnabled(JsonNode responseNode, String locator, List<ImportWarning> warnings) {
        boolean templated = false;
        JsonNode transformers = responseNode.path("transformers");
        if (transformers.isArray()) {
            for (JsonNode transformer : transformers) {
                if ("response-template".equals(transformer.asText())) {
                    templated = true;
                } else {
                    warnings.add(new ImportWarning(locator, "response.transformers." + transformer.asText(),
                        "custom response transformer '" + transformer.asText() + "' has no MockServer equivalent and is not applied"));
                }
            }
        }
        JsonNode parameters = responseNode.path("transformerParameters");
        if (parameters.isObject() && !parameters.isEmpty()) {
            warnings.add(new ImportWarning(locator, "response.transformerParameters",
                "transformer parameters " + fieldNames(parameters) + " are not mapped"));
        }
        return templated;
    }

    /**
     * Builds a Mustache response template when the WireMock template uses only references MockServer can
     * express. Returns null when there is nothing to template, or (with a warning) when a reference cannot
     * be translated, in which case the response is served verbatim.
     */
    private static HttpTemplate responseTemplate(HttpResponse httpResponse, String templateBody, WireMockTemplateTranslator.PathContext pathContext,
                                                 String locator, ImportRedaction.Options redactionOptions, List<ImportWarning> warnings) {
        List<String> untranslatable = new ArrayList<>();
        boolean hasTags = false;
        HttpResponse templated = httpResponse.shallowClone();
        String translatedBody = null;
        if (templateBody != null) {
            WireMockTemplateTranslator.Result result = WireMockTemplateTranslator.toMustache(templateBody, pathContext);
            translatedBody = result.text;
            hasTags = result.hasTags;
            untranslatable.addAll(result.untranslatable);
            String contentType = httpResponse.getBody() != null ? httpResponse.getBody().getContentType() : null;
            templated.withBody(result.text, contentType != null ? MediaType.parse(contentType) : null);
        }
        List<Header> headers = new ArrayList<>();
        for (Header header : httpResponse.getHeaderList()) {
            List<String> values = new ArrayList<>();
            for (NottableString value : header.getValues()) {
                WireMockTemplateTranslator.Result result = WireMockTemplateTranslator.toMustache(value.getValue(), pathContext);
                hasTags |= result.hasTags;
                untranslatable.addAll(result.untranslatable);
                values.add(result.text);
            }
            headers.add(Header.header(header.getName().getValue(), values));
        }
        templated.withHeaders(headers);
        if (!untranslatable.isEmpty()) {
            warnings.add(new ImportWarning(locator, "response.transformers.response-template",
                "the template uses " + untranslatable + ", which has no MockServer Mustache equivalent; the response is served verbatim"));
            return null;
        }
        if (!hasTags) {
            return null;
        }
        if (WireMockTemplateTranslator.referencesRequestValues(translatedBody) || headers.stream().anyMatch(header ->
            header.getValues().stream().anyMatch(value -> WireMockTemplateTranslator.referencesRequestValues(value.getValue())))) {
            warnings.add(new ImportWarning(locator, "response.transformers.response-template",
                "request values are rendered inside MockServer's JSON response template, so a backslash in one is read as a JSON escape (and a trailing backslash stops the response rendering)"));
        }
        try {
            // the template text is out of reach of the import redaction that runs later, so redact it here
            String template = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(new HttpResponseDTO(ImportRedaction.redactResponse(templated, redactionOptions)));
            return HttpTemplate.template(HttpTemplate.TemplateType.MUSTACHE, template);
        } catch (IOException e) {
            warnings.add(new ImportWarning(locator, "response.transformers.response-template",
                "could not build the response template (" + e.getMessage() + "); the response is served verbatim"));
            return null;
        }
    }

    /**
     * WireMock adds {@code fixedDelayMilliseconds} to a sampled {@code delayDistribution}; MockServer has a
     * single delay that is either fixed or sampled, so the fixed part is folded into the distribution.
     */
    private Delay mapDelay(JsonNode fixedNode, JsonNode distributionNode, String locator, String field, List<ImportWarning> warnings) {
        long fixed = fixedNode.isNumber() ? Math.max(0, fixedNode.asLong()) : 0;
        if (!distributionNode.isObject()) {
            return fixedNode.isNumber() ? Delay.milliseconds(fixed) : null;
        }
        String type = distributionNode.path("type").asText("").toLowerCase(Locale.ROOT);
        String construct = field + ".delayDistribution";
        try {
            switch (type) {
                case "uniform":
                    return new Delay(TimeUnit.MILLISECONDS, 0, DelayDistribution.uniform(
                        distributionNode.path("lower").asLong() + fixed, distributionNode.path("upper").asLong() + fixed));
                case "lognormal": {
                    double median = distributionNode.path("median").asDouble();
                    double sigma = distributionNode.path("sigma").asDouble();
                    long p99 = Math.round(median * Math.exp(sigma * Z_SCORE_P99));
                    if (fixed > 0) {
                        warnings.add(new ImportWarning(locator, construct,
                            "fixedDelayMilliseconds plus a lognormal distribution is approximated by a lognormal with the same median and 99th percentile"));
                    }
                    if (distributionNode.has("maxValue")) {
                        warnings.add(new ImportWarning(locator, construct + ".maxValue",
                            "the lognormal maxValue cap is not applied"));
                    }
                    return new Delay(TimeUnit.MILLISECONDS, 0, DelayDistribution.logNormal(Math.max(1, Math.round(median)) + fixed, Math.max(1, p99) + fixed));
                }
                case "fixed":
                    return Delay.milliseconds(fixed + Math.max(0, distributionNode.path("milliseconds").asLong()));
                default:
                    warnings.add(new ImportWarning(locator, construct,
                        "unknown delay distribution type '" + type + "'; only fixedDelayMilliseconds is applied"));
            }
        } catch (IllegalArgumentException e) {
            warnings.add(new ImportWarning(locator, construct,
                "invalid " + type + " delay distribution (" + e.getMessage() + "); only fixedDelayMilliseconds is applied"));
        }
        return fixedNode.isNumber() ? Delay.milliseconds(fixed) : null;
    }

    /**
     * {@code chunkedDribbleDelay} splits the body into {@code numberOfChunks} chunks spread over
     * {@code totalDuration}; MockServer's chunked connection options take a chunk size and a per-chunk delay.
     */
    private static ConnectionOptions mapChunkedDribble(JsonNode dribble, long bodyLength, boolean templated, String locator, List<ImportWarning> warnings) {
        if (!dribble.isObject()) {
            return null;
        }
        int chunks = dribble.path("numberOfChunks").asInt(0);
        long totalDuration = dribble.path("totalDuration").asLong(-1);
        if (chunks < 1 || totalDuration < 0) {
            warnings.add(new ImportWarning(locator, "response.chunkedDribbleDelay",
                "numberOfChunks must be at least 1 and totalDuration at least 0 — not applied"));
            return null;
        }
        if (bodyLength <= 0) {
            warnings.add(new ImportWarning(locator, "response.chunkedDribbleDelay",
                "the response has no body to dribble — not applied"));
            return null;
        }
        if (templated) {
            warnings.add(new ImportWarning(locator, "response.chunkedDribbleDelay",
                "the chunk size is computed from the unrendered template, so the rendered body may arrive in a different number of chunks"));
        }
        long chunkSize = Math.max(1, (bodyLength + chunks - 1) / chunks);
        return new ConnectionOptions()
            .withChunkSize((int) Math.min(Integer.MAX_VALUE, chunkSize))
            .withChunkDelay(Delay.milliseconds(totalDuration / chunks));
    }

    /**
     * Resolves {@code bodyFileName} inside the {@code __files} directory, refusing a name that escapes it.
     */
    private static Path resolveBodyFile(String bodyFileName, Path filesRoot, String locator, List<ImportWarning> warnings) {
        if (filesRoot == null) {
            warnings.add(new ImportWarning(locator, "response.bodyFileName",
                "bodyFileName '" + bodyFileName + "' is not resolved because no files directory was given; pass the WireMock __files directory as filesDirectory, or inline the body"));
            return null;
        }
        Path file = filesRoot.resolve(bodyFileName).normalize();
        if (!file.startsWith(filesRoot)) {
            warnings.add(new ImportWarning(locator, "response.bodyFileName",
                "bodyFileName '" + bodyFileName + "' resolves outside the files directory — response body omitted"));
            return null;
        }
        Path canonicalFile;
        try {
            canonicalFile = Files.isRegularFile(file) ? file.toRealPath() : null;
        } catch (IOException | SecurityException e) {
            canonicalFile = null;
        }
        if (canonicalFile == null) {
            warnings.add(new ImportWarning(locator, "response.bodyFileName",
                "bodyFileName '" + bodyFileName + "' was not found in " + filesRoot + " — response body omitted"));
            return null;
        }
        // a symbolic link inside the directory must not lead outside it
        if (!canonicalFile.startsWith(canonicalRoot(filesRoot))) {
            warnings.add(new ImportWarning(locator, "response.bodyFileName",
                "bodyFileName '" + bodyFileName + "' resolves outside the files directory — response body omitted"));
            return null;
        }
        return canonicalFile;
    }

    private static Path canonicalRoot(Path filesRoot) {
        try {
            return filesRoot.toRealPath();
        } catch (IOException | SecurityException e) {
            return filesRoot;
        }
    }

    /**
     * A templated body file is inlined so its references can be translated; returns null when the file has
     * no template references, so it is served from disk.
     */
    private static String readTemplatedFile(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return WireMockTemplateTranslator.hasTags(content) ? content : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static long fileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return -1;
        }
    }

    /**
     * EMPTY_RESPONSE, MALFORMED_RESPONSE_CHUNK and RANDOM_DATA_THEN_CLOSE are reproduced byte for byte on
     * HTTP/1.1 (HTTP/2 has no raw-bytes path, so only the drop applies). CONNECTION_RESET_BY_PEER resets the
     * connection with a TCP RST, as WireMock does.
     */
    private HttpError mapFault(String fault, String locator, List<ImportWarning> warnings) {
        switch (fault.toUpperCase(Locale.ROOT)) {
            case "EMPTY_RESPONSE":
                return HttpError.error().withDropConnection(true);
            case "MALFORMED_RESPONSE_CHUNK": {
                byte[] head = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
                byte[] bytes = new byte[head.length + WIREMOCK_GARBAGE.length];
                System.arraycopy(head, 0, bytes, 0, head.length);
                System.arraycopy(WIREMOCK_GARBAGE, 0, bytes, head.length, WIREMOCK_GARBAGE.length);
                return HttpError.error().withResponseBytes(bytes).withDropConnection(true);
            }
            case "RANDOM_DATA_THEN_CLOSE":
                return HttpError.error().withResponseBytes(WIREMOCK_GARBAGE.clone()).withDropConnection(true);
            case "CONNECTION_RESET_BY_PEER":
                return HttpError.error().withResetConnection(true);
            default:
                warnings.add(new ImportWarning(locator, "response.fault." + fault,
                    "unknown fault type — approximated as a dropped connection"));
                return HttpError.error().withDropConnection(true);
        }
    }

    /**
     * Maps WireMock webhooks ({@code postServeActions} in WireMock 2, {@code serveEventListeners} in
     * WireMock 3) to after-actions, which MockServer sends once the response has been served.
     */
    private void applyWebhooks(Expectation expectation, JsonNode stub, String locator, List<ImportWarning> warnings) {
        List<AfterAction> afterActions = new ArrayList<>();
        for (String field : List.of("postServeActions", "serveEventListeners")) {
            JsonNode listeners = stub.path(field);
            if (listeners.isArray()) {
                int i = 0;
                for (JsonNode listener : listeners) {
                    addWebhook(afterActions, listener.path("name").asText(""), listener.path("parameters"), locator + " " + field + "[" + i++ + "]", field, warnings);
                }
            } else if (listeners.isObject()) {
                listeners.fields().forEachRemaining(entry ->
                    addWebhook(afterActions, entry.getKey(), entry.getValue(), locator + " " + field + "." + entry.getKey(), field, warnings));
            }
        }
        if (!afterActions.isEmpty()) {
            expectation.withAfterActions(afterActions);
        }
    }

    private void addWebhook(List<AfterAction> afterActions, String name, JsonNode parameters, String locator, String field, List<ImportWarning> warnings) {
        if (!"webhook".equals(name)) {
            warnings.add(new ImportWarning(locator, field + "." + name,
                "serve event listener '" + name + "' has no MockServer equivalent and is not applied"));
            return;
        }
        List<String> untranslatable = new ArrayList<>();
        String url = runtimeExpressions(textOrNull(parameters, "url"), untranslatable);
        Matcher urlParts = url == null ? null : URL_PARTS.matcher(url);
        if (urlParts == null || !urlParts.matches()) {
            warnings.add(new ImportWarning(locator, field + ".webhook",
                "webhook url " + (url == null ? "is missing" : "'" + url + "' is not an absolute http(s) URL") + " — webhook not applied"));
            return;
        }
        String path = urlParts.group(3);
        HttpRequest webhook = request()
            .withMethod(parameters.path("method").asText("GET").toUpperCase(Locale.ROOT))
            .withSecure("https".equalsIgnoreCase(urlParts.group(1)))
            .withPath(path.isEmpty() ? "/" : path)
            .withHeader("Host", urlParts.group(2));
        List<Parameter> query = parseQueryString(urlParts.group(4));
        if (!query.isEmpty()) {
            webhook.withQueryStringParameters(query);
        }
        JsonNode headers = parameters.path("headers");
        if (headers.isObject()) {
            headers.fields().forEachRemaining(header -> {
                List<String> values = new ArrayList<>();
                (header.getValue().isArray() ? header.getValue() : List.of(header.getValue())).forEach(v -> values.add(runtimeExpressions(v.asText(), untranslatable)));
                webhook.withHeader(header.getKey(), values.toArray(new String[0]));
            });
        }
        String body = textOrNull(parameters, "body");
        String base64Body = textOrNull(parameters, "base64Body");
        if (body != null) {
            webhook.withBody(runtimeExpressions(body, untranslatable));
        } else if (base64Body != null) {
            try {
                webhook.withBody(BinaryBody.binary(Base64.getDecoder().decode(base64Body)));
            } catch (IllegalArgumentException e) {
                warnings.add(new ImportWarning(locator, field + ".webhook.base64Body", "invalid Base64 — webhook body omitted"));
            }
        }
        if (!untranslatable.isEmpty()) {
            warnings.add(new ImportWarning(locator, field + ".webhook",
                "webhook template references " + untranslatable + " have no MockServer equivalent and are sent verbatim"));
        }
        AfterAction afterAction = AfterAction.afterAction().withHttpRequest(webhook);
        Delay delay = mapDelay(MissingNode.getInstance(), parameters.path("delay"), locator, field + ".webhook", warnings);
        if (delay != null) {
            afterAction.withDelay(delay);
        }
        afterActions.add(afterAction);
    }

    private static String runtimeExpressions(String text, List<String> untranslatable) {
        if (text == null) {
            return null;
        }
        WireMockTemplateTranslator.Result result = WireMockTemplateTranslator.toRuntimeExpressions(text);
        untranslatable.addAll(result.untranslatable);
        return result.text;
    }

    private HttpForward mapForward(String proxyBaseUrl, String locator, List<ImportWarning> warnings) {
        HttpForward forward = HttpForward.forward();
        try {
            URI uri = new URI(proxyBaseUrl);
            forward.withHost(uri.getHost());
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            forward.withScheme(https ? HttpForward.Scheme.HTTPS : HttpForward.Scheme.HTTP);
            int port = uri.getPort();
            forward.withPort(port > 0 ? port : (https ? 443 : 80));
            if (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath())) {
                warnings.add(new ImportWarning(locator, "response.proxyBaseUrl",
                    "proxyBaseUrl path prefix '" + uri.getPath() + "' is not applied; MockServer forwards to host/port only"));
            }
        } catch (Exception e) {
            warnings.add(new ImportWarning(locator, "response.proxyBaseUrl",
                "could not parse proxyBaseUrl '" + proxyBaseUrl + "' — forward target may be incomplete"));
        }
        return forward;
    }

    private List<Parameter> parseQueryString(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return List.of();
        }
        List<Parameter> params = new ArrayList<>();
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq >= 0) {
                params.add(Parameter.param(decodeComponent(pair.substring(0, eq)), decodeComponent(pair.substring(eq + 1))));
            } else {
                params.add(Parameter.param(decodeComponent(pair), ""));
            }
        }
        return params;
    }

    private String idSuffix(JsonNode stub) {
        String id = textOrNull(stub, "id");
        if (id == null) {
            id = textOrNull(stub, "uuid");
        }
        if (id == null) {
            String name = textOrNull(stub, "name");
            id = name != null ? name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "") : null;
        }
        return id != null && !id.isEmpty() ? "-" + id : "";
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

    private static String decodeComponent(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
