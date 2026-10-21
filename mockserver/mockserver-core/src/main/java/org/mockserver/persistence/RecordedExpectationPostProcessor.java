package org.mockserver.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.filters.TransportHeaderFilter;
import org.mockserver.imports.HarImporter;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.ResponseMode;
import org.mockserver.model.Body;
import org.mockserver.model.Header;
import org.mockserver.model.Headers;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;
import org.mockserver.model.MediaType;
import org.mockserver.model.NottableString;
import org.mockserver.model.Parameter;
import org.mockserver.model.Parameters;
import org.mockserver.model.RequestDefinition;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Opt-in post-processing of recorded (proxy SPY/CAPTURE) expectations that
 * (a) <b>deduplicates</b> structurally-identical recorded request/response pairs,
 * keeping a single representative, and (b) <b>templatizes</b> variable path
 * segments so that, for example, recorded calls to {@code /users/1},
 * {@code /users/2} and {@code /users/3} collapse into a single expectation that
 * matches {@code /users/{id}} (a declared MockServer path parameter, which the
 * matcher normalises to a {@code .*} regex segment).
 *
 * <p>This is a pure function over {@code List<Expectation>} so it can be tested
 * in isolation and invoked from a recording/retrieve path without side effects.
 * It is intentionally <b>conservative</b>:
 * <ul>
 *   <li>Only HTTP requests with a concrete {@link HttpResponse} are considered;
 *       any other expectation (callbacks, forwards, OpenAPI matchers, etc.) is
 *       passed through unchanged and never merged.</li>
 *   <li>A path segment is only treated as variable when it <i>looks like an id</i>
 *       — all digits, or a canonical UUID. Nothing else is collapsed.</li>
 *   <li>A group of requests is only collapsed into one templated expectation
 *       when the non-variable parts (method, every fixed path segment, and the
 *       request body shape) match exactly, the variable segments occupy the same
 *       positions, and <b>all responses in the group are equal</b>. If responses
 *       differ, the group is left as distinct expectations (we never merge
 *       differing responses under one matcher).</li>
 *   <li>Exact duplicates (same request signature and equal response) always
 *       collapse to a single expectation, even when there is no variable
 *       segment to templatize.</li>
 * </ul>
 *
 * <p>Order is preserved: the first expectation of each retained group keeps its
 * original position in the output.
 *
 * <p><b>Value templatization (opt-in, additive).</b> When the caller passes
 * {@code templatizeValues = true} a second, equally-conservative generalisation
 * runs on each retained expectation: volatile-looking <i>query parameter</i>,
 * <i>header</i> and <i>JSON body leaf</i> values — ids, UUIDs, ISO-8601 or
 * epoch-millis timestamps, and long opaque tokens (JWT / base64 / hex) — are
 * replaced with regex matchers so the recorded expectation is reusable instead
 * of pinned to one captured value. Stable values (short strings, words,
 * booleans, enums, small numbers, common content-types) are preserved verbatim.
 * The two-argument {@link #deduplicateAndTemplatize(List)} overload keeps the
 * historical path-only behaviour for full back-compat; value templatization only
 * happens through {@link #deduplicateAndTemplatize(List, boolean)} with the flag
 * set (wired behind the {@code templatizeRecordedValues} configuration option).
 */
public class RecordedExpectationPostProcessor {

    /**
     * Matches a path segment that is purely numeric, e.g. {@code 123}.
     */
    private static final Pattern NUMERIC_ID = Pattern.compile("\\d+");

    /**
     * Matches a canonical UUID path segment (8-4-4-4-12 hex), case-insensitive.
     */
    private static final Pattern UUID_ID = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /**
     * Placeholder used internally to key a path template by position; the
     * rendered path parameter name is derived per-position (see
     * {@link #parameterName(int)}). A NUL character is used because it can never
     * appear in a (decoded) URL path segment, so it can never collide with a
     * literal segment value (a literal space, for example, can).
     */
    private static final String VARIABLE_PLACEHOLDER = "\u0000";

    // ----- volatile VALUE heuristics (query params, headers, JSON body) -------

    /**
     * Canonical UUID (whole value).
     */
    private static final Pattern UUID_VALUE = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /**
     * A purely-numeric id that is long enough to look like an identifier rather
     * than a stable small constant (page size, version, count). Six or more
     * digits avoids generalising values like {@code 1}, {@code 200} or {@code 42}.
     */
    private static final Pattern LONG_NUMERIC_ID = Pattern.compile("^\\d{6,}$");

    /**
     * ISO-8601 date or date-time, e.g. {@code 2026-06-20T12:34:56Z} or
     * {@code 2026-06-20T12:34:56.123+01:00} or a bare {@code 2026-06-20}.
     */
    private static final Pattern ISO_8601 = Pattern.compile(
        "^\\d{4}-\\d{2}-\\d{2}([Tt ]\\d{2}:\\d{2}(:\\d{2})?(\\.\\d{1,9})?([Zz]|[+-]\\d{2}:?\\d{2})?)?$");

    /**
     * Epoch milliseconds (13 digits) — a common volatile timestamp form. Plain
     * 10-digit epoch seconds are intentionally excluded because they overlap with
     * ordinary long ids and are caught by {@link #LONG_NUMERIC_ID} anyway.
     */
    private static final Pattern EPOCH_MILLIS = Pattern.compile("^1\\d{12}$");

    /**
     * A JWT (three base64url segments separated by dots).
     */
    private static final Pattern JWT = Pattern.compile(
        "^[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}$");

    /**
     * A long opaque token — a run of at least 24 characters drawn only from the
     * base64/base64url alphabet. Long enough that ordinary words, slugs and
     * stable identifiers are not mistaken for credentials. The
     * {@link #isOpaqueToken(String)} guard additionally requires <i>mixed</i>
     * content (both letters and digits, or some lowercase) so stable
     * SCREAMING_SNAKE_CASE enum constants and all-uppercase codes are kept
     * verbatim rather than generalised.
     */
    private static final Pattern OPAQUE_TOKEN = Pattern.compile("^[A-Za-z0-9_=+/-]{24,}$");

    /**
     * Header names whose values are credentials/correlation ids and are always
     * volatile when present (matched case-insensitively).
     */
    private static final Set<String> VOLATILE_HEADER_NAMES = Set.of(
        "authorization", "proxy-authorization", "cookie", "set-cookie",
        "x-api-key", "api-key", "x-request-id", "x-correlation-id",
        "x-amz-date", "x-amz-security-token", "x-csrf-token", "x-xsrf-token");

    /**
     * For query params and headers the value string itself is treated as a regex
     * by the matcher, so {@code .+} matches any non-empty value. For JSON bodies
     * json-unit's built-in {@code ${json-unit.regex}} placeholder is used so only
     * the targeted leaf is generalised.
     */
    private static final String ANY_VALUE_REGEX = ".+";
    private static final String JSON_UNIT_REGEX_PREFIX = "${json-unit.regex}";

    private RecordedExpectationPostProcessor() {
    }

    /**
     * Deduplicate and templatize a list of recorded expectations. Pure function:
     * the input list and its elements are not mutated.
     *
     * @param expectations recorded expectations (may be {@code null} or empty)
     * @return a new list with structurally-identical requests deduplicated and
     * variable id path segments collapsed into templated expectations
     */
    public static List<Expectation> deduplicateAndTemplatize(List<Expectation> expectations) {
        return deduplicateAndTemplatize(expectations, false);
    }

    /**
     * Deduplicate and templatize a list of recorded expectations. Pure function:
     * the input list and its elements are not mutated.
     *
     * @param expectations    recorded expectations (may be {@code null} or empty)
     * @param templatizeValues when {@code true}, also generalise volatile-looking
     *                         query parameter, header and JSON body leaf values
     *                         into regex matchers (ids, UUIDs, timestamps, tokens);
     *                         when {@code false} those values are kept verbatim
     *                         (historical, fully back-compatible behaviour)
     * @return a new list with structurally-identical requests deduplicated,
     * variable id path segments collapsed, and — when {@code templatizeValues} is
     * set — volatile values generalised into matchers
     */
    public static List<Expectation> deduplicateAndTemplatize(List<Expectation> expectations, boolean templatizeValues) {
        List<Expectation> result = new ArrayList<>();
        if (expectations == null || expectations.isEmpty()) {
            return result;
        }

        // First pass: bucket eligible (HTTP request + concrete response)
        // expectations by their structural signature (method + templatized path
        // shape + body shape). Ineligible expectations are not grouped.
        Map<String, List<Expectation>> groups = new LinkedHashMap<>();
        for (Expectation expectation : expectations) {
            if (!isEligible(expectation)) {
                continue;
            }
            String signature = structuralSignature((HttpRequest) expectation.getHttpRequest());
            groups.computeIfAbsent(signature, k -> new ArrayList<>()).add(expectation);
        }

        // Second pass: emit in ORIGINAL first-seen order. Ineligible items stay
        // in place; the first time a group's signature is encountered, that
        // group's collapsed output is emitted at that position. This preserves
        // the relative order of every output element, which matters because
        // MockServer matches expectations in insertion order.
        Set<String> emitted = new HashSet<>();
        for (Expectation expectation : expectations) {
            if (!isEligible(expectation)) {
                result.add(expectation);
                continue;
            }
            String signature = structuralSignature((HttpRequest) expectation.getHttpRequest());
            if (emitted.add(signature)) {
                for (Expectation collapsed : collapseGroup(groups.get(signature))) {
                    result.add(templatizeValues ? templatizeValues(collapsed) : collapsed);
                }
            }
        }

        return result;
    }

    /**
     * Consolidate a list of recorded expectations into a compact set of reusable
     * <b>active mocks</b> — the "record&rarr;mock" engine. This differs from
     * {@link #deduplicateAndTemplatize(List, boolean)} (which preserves the
     * recorded {@link Times}/{@link org.mockserver.matchers.TimeToLive} and emits
     * one expectation per distinct response) in three deliberate ways, so the
     * output is directly usable as long-lived mocks rather than a faithful replay
     * of what was recorded:
     * <ol>
     *   <li><b>One expectation per request shape.</b> All exchanges sharing a
     *       structural signature (method + templatized path shape + body shape)
     *       collapse into a single expectation. Where the group spans several
     *       concrete ids the varying id path segments become {@code {id}} path
     *       parameters (path inference); otherwise the concrete path is kept.</li>
     *   <li><b>Unlimited times/TTL.</b> Every consolidated expectation is emitted
     *       with {@link Times#unlimited()} and
     *       {@link org.mockserver.matchers.TimeToLive#unlimited()} so it keeps
     *       matching, replacing the recorded {@code Times.once()} that makes raw
     *       recordings brittle.</li>
     *   <li><b>Differing responses are sequenced.</b> When the same request shape
     *       produced several <i>distinct</i> responses they are attached as a
     *       {@link ResponseMode#SEQUENTIAL} response list (first-seen order,
     *       de-duplicated) rather than fanned out into multiple competing
     *       expectations.</li>
     * </ol>
     * Volatile request headers (reusing {@link HarImporter#volatileRequestHeaders()})
     * are always stripped from the generated matcher. When
     * {@code parameterizeValues} is set, volatile-looking query parameter, header
     * and JSON body leaf values are additionally generalised to regex matchers
     * (the same heuristics used by {@link #deduplicateAndTemplatize(List, boolean)}).
     *
     * <p>Ineligible expectations (no concrete {@link HttpResponse} — callbacks,
     * forwards, errors, OpenAPI matchers, etc.) are passed through unchanged in
     * their original position. Pure function: the input and its elements are not
     * mutated.
     *
     * @param expectations       recorded expectations (may be {@code null}/empty)
     * @param parameterizeValues when {@code true}, also generalise volatile query
     *                           parameter, header and JSON body leaf values
     * @return the consolidated expectations
     */
    public static List<Expectation> consolidate(List<Expectation> expectations, boolean parameterizeValues) {
        List<Expectation> result = new ArrayList<>();
        if (expectations == null || expectations.isEmpty()) {
            return result;
        }

        // Bucket eligible expectations by structural signature, preserving order.
        Map<String, List<Expectation>> groups = new LinkedHashMap<>();
        for (Expectation expectation : expectations) {
            if (!isEligible(expectation)) {
                continue;
            }
            String signature = structuralSignature((HttpRequest) expectation.getHttpRequest());
            groups.computeIfAbsent(signature, k -> new ArrayList<>()).add(expectation);
        }

        // Emit in original first-seen order: ineligible items stay in place, each
        // group is emitted once (as a single consolidated expectation) at the
        // position of its first member.
        Set<String> emitted = new HashSet<>();
        for (Expectation expectation : expectations) {
            if (!isEligible(expectation)) {
                result.add(expectation);
                continue;
            }
            String signature = structuralSignature((HttpRequest) expectation.getHttpRequest());
            if (emitted.add(signature)) {
                result.add(consolidateGroup(groups.get(signature), parameterizeValues));
            }
        }

        return result;
    }

    /**
     * Keep only the named request headers in each recorded request matcher, so a recording matches on the
     * headers that matter rather than every header the client happened to send. Names are compared
     * case-insensitively; an empty collection removes every header. Every other part of each expectation is
     * kept. Pure function: the input and its elements are not mutated.
     *
     * @param expectations recorded expectations (may be {@code null} or empty)
     * @param headerNames  the request header names to keep
     * @return the expectations with every other request header removed from their matchers
     */
    public static List<Expectation> captureHeaders(List<Expectation> expectations, Collection<String> headerNames) {
        List<Expectation> result = new ArrayList<>();
        if (expectations == null || expectations.isEmpty()) {
            return result;
        }
        Set<String> keep = new HashSet<>();
        for (String name : headerNames) {
            keep.add(name.toLowerCase(Locale.ROOT));
        }
        for (Expectation expectation : expectations) {
            if (expectation == null || !(expectation.getHttpRequest() instanceof HttpRequest)) {
                result.add(expectation);
                continue;
            }
            HttpRequest request = (HttpRequest) expectation.getHttpRequest();
            HttpRequest selected = withoutHeaders(request, name -> !keep.contains(name.toLowerCase(Locale.ROOT)));
            result.add(selected == request ? expectation : expectation.cloneWith(selected, expectation.getHttpResponse()));
        }
        return result;
    }

    /**
     * Turn a request that was recorded several times with <b>different</b> responses into one expectation that
     * replays those responses in the order they were recorded (WireMock's {@code repeatsAsScenarios}). The
     * expectation uses {@link ResponseMode#SWITCH}, so after the last recorded response it keeps returning that
     * last response, and is unlimited so it can be matched once per recorded response and beyond. It takes the
     * position of the first recorded request and uses that request, unchanged, as its matcher.
     *
     * <p>Two recordings are the same request only when their matchers would match the same requests: every
     * matched property is compared, every header included. Only the client and local address and the HTTP/2
     * stream id, which the matcher ignores, are left out. Requests that differ in any header, such as
     * {@code Host}, {@code Authorization} or {@code Accept}, therefore stay separate expectations; apply
     * {@link #captureHeaders(List, Collection)} first to choose which headers tell requests apart.
     *
     * <p>Responses are compared without the headers that change on every response
     * ({@link HarImporter#volatileResponseHeaders()}, such as {@code Date}). A request recorded with the same
     * response every time, or recorded once, is left exactly as it was. Expectations without a single concrete
     * response pass through unchanged. Pure function: the input and its elements are not mutated.
     *
     * @param expectations recorded expectations (may be {@code null} or empty)
     * @return the expectations with each differently-answered repeated request replaced by one response sequence
     */
    public static List<Expectation> sequenceRepeats(List<Expectation> expectations) {
        List<Expectation> result = new ArrayList<>();
        if (expectations == null || expectations.isEmpty()) {
            return result;
        }
        // linear: each key and each normalised response is computed once, and each group is decided once
        HttpRequest[] keys = new HttpRequest[expectations.size()];
        Map<HttpRequest, List<Expectation>> groups = new LinkedHashMap<>();
        for (int i = 0; i < keys.length; i++) {
            Expectation expectation = expectations.get(i);
            if (isEligible(expectation)) {
                keys[i] = repeatKey(expectation);
                groups.computeIfAbsent(keys[i], k -> new ArrayList<>()).add(expectation);
            }
        }
        Map<HttpRequest, Expectation> sequences = new HashMap<>();
        Set<HttpRequest> emitted = new HashSet<>();
        for (Map.Entry<HttpRequest, List<Expectation>> group : groups.entrySet()) {
            if (hasDifferentResponses(group.getValue())) {
                sequences.put(group.getKey(), responseSequence(group.getValue()));
            }
        }
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == null) {
                result.add(expectations.get(i));
                continue;
            }
            Expectation sequence = sequences.get(keys[i]);
            if (sequence == null) {
                result.add(expectations.get(i));
            } else if (emitted.add(keys[i])) {
                result.add(sequence);
            }
        }
        return result;
    }

    private static Expectation responseSequence(List<Expectation> group) {
        Expectation first = group.get(0);
        List<HttpResponse> responses = new ArrayList<>(group.size());
        for (Expectation expectation : group) {
            responses.add(expectation.getHttpResponse());
        }
        return new Expectation(first.getHttpRequest(), Times.unlimited(), TimeToLive.unlimited(), first.getPriority())
            .withResponseMode(ResponseMode.SWITCH)
            .thenRespond(responses);
    }

    /**
     * A copy of the recorded request without the per-connection fields the request matcher never compares.
     */
    private static HttpRequest repeatKey(Expectation expectation) {
        return ((HttpRequest) expectation.getHttpRequest()).clone()
            .withStreamId(null)
            .withLocalAddress(null)
            .withRemoteAddress(null);
    }

    private static boolean hasDifferentResponses(List<Expectation> group) {
        if (group.size() < 2) {
            return false;
        }
        HttpResponse first = withoutVolatileResponseHeaders(group.get(0).getHttpResponse());
        for (int i = 1; i < group.size(); i++) {
            if (!first.equals(withoutVolatileResponseHeaders(group.get(i).getHttpResponse()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * A copy of {@code response} without its volatile headers and without its parsed cookies, which a proxied
     * response carries alongside its {@code Set-Cookie} headers.
     */
    private static HttpResponse withoutVolatileResponseHeaders(HttpResponse response) {
        HttpResponse copy = response.clone();
        Headers headers = response.getHeaders();
        if (headers != null && !headers.isEmpty()) {
            for (Header header : headers.getEntries()) {
                String name = header.getName() != null ? header.getName().getValue() : null;
                if (name != null && HarImporter.volatileResponseHeaders().contains(name.toLowerCase(Locale.ROOT))) {
                    copy.removeHeader(header.getName());
                }
            }
            if (copy.getHeaders() != null && copy.getHeaders().isEmpty()) {
                copy.withHeaders((Headers) null);
            }
        }
        return copy.withCookies((org.mockserver.model.Cookies) null);
    }

    /**
     * A copy of {@code request} without the headers whose name {@code remove} accepts, or {@code request}
     * itself when none are removed. An emptied header list becomes no headers.
     */
    private static HttpRequest withoutHeaders(HttpRequest request, java.util.function.Predicate<String> remove) {
        Headers headers = request.getHeaders();
        if (headers == null || headers.isEmpty()) {
            return request;
        }
        List<NottableString> toRemove = new ArrayList<>();
        for (Header entry : headers.getEntries()) {
            if (entry.getName() != null && entry.getName().getValue() != null && remove.test(entry.getName().getValue())) {
                toRemove.add(entry.getName());
            }
        }
        if (toRemove.isEmpty()) {
            return request;
        }
        HttpRequest copy = request.clone();
        for (NottableString name : toRemove) {
            copy.removeHeader(name);
        }
        if (copy.getHeaders() != null && copy.getHeaders().isEmpty()) {
            copy.withHeaders((Headers) null);
        }
        return copy;
    }

    /**
     * Collapse a structural-signature group into a single unlimited-times mock.
     * Distinct responses are sequenced (SEQUENTIAL) when there is more than one.
     */
    private static Expectation consolidateGroup(List<Expectation> group, boolean parameterizeValues) {
        HttpRequest representative = (HttpRequest) group.get(0).getHttpRequest();

        HttpRequest consolidatedRequest;
        if (hasVariableSegment(representative) && partitionSpansMultiplePaths(group)) {
            consolidatedRequest = templatizedRequest(representative);
        } else {
            consolidatedRequest = representative.clone();
        }

        stripVolatileHeaders(consolidatedRequest);
        if (parameterizeValues) {
            generalizeQueryParameters(consolidatedRequest);
            generalizeHeaders(consolidatedRequest);
            generalizeJsonBody(consolidatedRequest);
        }

        List<HttpResponse> responses = distinctResponses(group);
        Expectation consolidated = new Expectation(consolidatedRequest, Times.unlimited(), TimeToLive.unlimited(), 0);
        if (responses.size() <= 1) {
            consolidated.thenRespond(responses.isEmpty() ? null : responses.get(0));
        } else {
            consolidated.withResponseMode(ResponseMode.SEQUENTIAL).thenRespond(responses);
        }
        return consolidated;
    }

    /**
     * Distinct concrete responses of a group, in first-seen order. Equal responses
     * collapse (so 50 identical hits yield one response); differing responses are
     * preserved for SEQUENTIAL replay.
     */
    private static List<HttpResponse> distinctResponses(List<Expectation> group) {
        Set<HttpResponse> seen = new LinkedHashSet<>();
        for (Expectation expectation : group) {
            HttpResponse response = expectation.getHttpResponse();
            if (response != null) {
                seen.add(response);
            }
        }
        return new ArrayList<>(seen);
    }

    /**
     * Remove volatile request headers from a generated matcher: the volatile-header set applied
     * to HAR imports ({@link HarImporter#volatileRequestHeaders()}) plus every hop-by-hop and
     * transport header ({@link TransportHeaderFilter#isTransportHeader(String)}).
     */
    private static void stripVolatileHeaders(HttpRequest request) {
        Headers headers = request.getHeaders();
        if (headers == null || headers.isEmpty()) {
            return;
        }
        Set<String> volatileHeaders = HarImporter.volatileRequestHeaders();
        for (Header entry : new ArrayList<>(headers.getEntries())) {
            String name = entry.getName() != null ? entry.getName().getValue() : null;
            if (name != null && (volatileHeaders.contains(name.toLowerCase(Locale.ROOT)) || TransportHeaderFilter.isTransportHeader(name))) {
                request.removeHeader(name);
            }
        }
    }

    private static boolean isEligible(Expectation expectation) {
        return expectation != null
            && expectation.getHttpRequest() instanceof HttpRequest
            && expectation.getHttpResponse() != null;
    }

    /**
     * Collapse a group of expectations that share a structural signature. The
     * group is partitioned by response: each distinct response yields one
     * expectation. Templatization is decided <b>per partition</b>: a partition
     * is only templatized when it has a variable path segment <i>and</i> spans
     * more than one distinct concrete path. A partition with a single concrete
     * id keeps its concrete path, so we never widen a single recorded id to a
     * {@code {id}} wildcard (which would have no de-duplication benefit).
     */
    private static List<Expectation> collapseGroup(List<Expectation> group) {
        List<Expectation> output = new ArrayList<>();
        if (group.size() == 1) {
            output.add(group.get(0));
            return output;
        }

        // Partition by response so differing responses are never merged.
        // LinkedHashMap keyed by response preserves first-seen order.
        Map<HttpResponse, List<Expectation>> byResponse = new LinkedHashMap<>();
        for (Expectation expectation : group) {
            byResponse.computeIfAbsent(expectation.getHttpResponse(), k -> new ArrayList<>()).add(expectation);
        }

        for (List<Expectation> partition : byResponse.values()) {
            Expectation representative = partition.get(0);
            if (hasVariableSegment((HttpRequest) representative.getHttpRequest())
                && partitionSpansMultiplePaths(partition)) {
                output.add(templatize(representative));
            } else {
                // No templatization: exact-duplicate dedup keeps the first.
                output.add(representative);
            }
        }
        return output;
    }

    /**
     * True when this response partition contains more than one distinct concrete
     * path. This guards against templatizing a single recorded id within a
     * partition (which would over-widen the matcher with no de-duplication
     * benefit) while still allowing the {@code /users/1,2,3} case to collapse.
     */
    private static boolean partitionSpansMultiplePaths(List<Expectation> partition) {
        String firstPath = ((HttpRequest) partition.get(0).getHttpRequest()).getPath().getValue();
        for (Expectation expectation : partition) {
            String path = ((HttpRequest) expectation.getHttpRequest()).getPath().getValue();
            if (firstPath == null ? path != null : !firstPath.equals(path)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasVariableSegment(HttpRequest request) {
        for (String segment : pathSegments(request)) {
            if (isVariableSegment(segment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Structural signature = method + templatized path shape + body shape. Two
     * requests share a signature when they could collapse into one templated
     * matcher (fixed segments equal, variable segments in the same positions).
     */
    private static String structuralSignature(HttpRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("M:").append(nullableValue(request.getMethod())).append('\n');
        builder.append("P:");
        String[] segments = pathSegments(request);
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                builder.append('/');
            }
            builder.append(isVariableSegment(segments[i]) ? VARIABLE_PLACEHOLDER : segments[i]);
        }
        if (endsWithSlash(request)) {
            builder.append('/');
        }
        builder.append('\n');
        builder.append("B:").append(bodyShape(request));
        return builder.toString();
    }

    /**
     * Build a templated copy of the representative request, replacing each
     * variable segment with a {@code {paramN}} path parameter and declaring the
     * parameter so MockServer's path matcher treats it as a wildcard segment.
     * The response and all other request properties are preserved.
     */
    private static Expectation templatize(Expectation representative) {
        return rebuild(representative, templatizedRequest((HttpRequest) representative.getHttpRequest()));
    }

    /**
     * Build a templated copy of {@code original}, replacing each variable id path
     * segment with a {@code {paramN}} path parameter and declaring the parameter so
     * MockServer's path matcher treats it as a wildcard segment. All other request
     * properties are preserved. Pure: {@code original} is not mutated.
     */
    private static HttpRequest templatizedRequest(HttpRequest original) {
        HttpRequest templated = original.clone();

        String[] segments = pathSegments(original);
        List<String> renderedSegments = new ArrayList<>();
        List<String> parameterNames = new ArrayList<>();
        for (String segment : segments) {
            if (isVariableSegment(segment)) {
                String name = parameterName(parameterNames.size());
                parameterNames.add(name);
                renderedSegments.add("{" + name + "}");
            } else {
                renderedSegments.add(segment);
            }
        }
        String leading = original.getPath().getValue().startsWith("/") ? "/" : "";
        String joined = leading + String.join("/", trimLeadingEmpty(renderedSegments));
        if (endsWithSlash(original) && !joined.endsWith("/")) {
            joined = joined + "/";
        }
        templated.withPath(joined);
        // Declare each path parameter; the matcher normalises {name} to a
        // wildcard segment, and a declared parameter keeps the path valid.
        for (String name : parameterNames) {
            templated.withPathParameter(name, ".*");
        }

        return templated;
    }

    /**
     * Recreate an Expectation with a replacement request, copying the response
     * and the identifying/priority fields. We never mutate the original.
     */
    private static Expectation rebuild(Expectation source, RequestDefinition newRequest) {
        Expectation rebuilt = new Expectation(
            newRequest,
            source.getTimes() != null ? source.getTimes().clone() : null,
            source.getTimeToLive(),
            source.getPriority()
        );
        rebuilt.thenRespond(source.getHttpResponse());
        return rebuilt;
    }

    // ----- value templatization (query params, headers, JSON body) -----------

    /**
     * Return a copy of {@code expectation} whose request has volatile-looking
     * query parameter, header and JSON body leaf values replaced with regex
     * matchers. Conservative: only values that match a volatile heuristic (id,
     * UUID, timestamp, token) are generalised; everything else is preserved. If
     * nothing volatile is found the original expectation is returned unchanged.
     */
    private static Expectation templatizeValues(Expectation expectation) {
        if (!(expectation.getHttpRequest() instanceof HttpRequest)) {
            return expectation;
        }
        HttpRequest original = (HttpRequest) expectation.getHttpRequest();
        HttpRequest copy = original.clone();
        boolean changed = false;

        changed |= generalizeQueryParameters(copy);
        changed |= generalizeHeaders(copy);
        changed |= generalizeJsonBody(copy);

        return changed ? rebuild(expectation, copy) : expectation;
    }

    private static boolean generalizeQueryParameters(HttpRequest request) {
        Parameters parameters = request.getQueryStringParameters();
        if (parameters == null || parameters.isEmpty()) {
            return false;
        }
        List<Parameter> rebuilt = new ArrayList<>();
        boolean changed = false;
        for (Parameter entry : parameters.getEntries()) {
            List<NottableString> newValues = new ArrayList<>();
            for (NottableString value : entry.getValues()) {
                if (!value.isNot() && isVolatileValue(value.getValue())) {
                    newValues.add(NottableString.string(ANY_VALUE_REGEX, value.isNot()));
                    changed = true;
                } else {
                    newValues.add(value);
                }
            }
            rebuilt.add(new Parameter(entry.getName(), newValues));
        }
        if (changed) {
            Parameters replacement = new Parameters(rebuilt);
            replacement.withKeyMatchStyle(parameters.getKeyMatchStyle());
            request.withQueryStringParameters(replacement);
        }
        return changed;
    }

    private static boolean generalizeHeaders(HttpRequest request) {
        Headers headers = request.getHeaders();
        if (headers == null || headers.isEmpty()) {
            return false;
        }
        List<Header> rebuilt = new ArrayList<>();
        boolean changed = false;
        for (Header entry : headers.getEntries()) {
            String name = entry.getName() != null ? entry.getName().getValue() : null;
            boolean nameIsVolatile = name != null && VOLATILE_HEADER_NAMES.contains(name.toLowerCase());
            List<NottableString> newValues = new ArrayList<>();
            for (NottableString value : entry.getValues()) {
                if (!value.isNot() && (nameIsVolatile || isVolatileValue(value.getValue()))) {
                    newValues.add(NottableString.string(ANY_VALUE_REGEX, value.isNot()));
                    changed = true;
                } else {
                    newValues.add(value);
                }
            }
            rebuilt.add(new Header(entry.getName(), newValues));
        }
        if (changed) {
            Headers replacement = new Headers(rebuilt);
            replacement.withKeyMatchStyle(headers.getKeyMatchStyle());
            request.withHeaders(replacement);
        }
        return changed;
    }

    /**
     * Generalise volatile string leaves inside a JSON body. The body is converted
     * to a {@link JsonBody} matching only the fields present (so generalised
     * leaves match any value of the right pattern via json-unit's
     * {@code ${json-unit.regex}} placeholder). Non-JSON bodies are left untouched.
     */
    private static boolean generalizeJsonBody(HttpRequest request) {
        Body<?> body = request.getBody();
        if (body == null) {
            return false;
        }
        String json = jsonStringBody(body);
        if (json == null) {
            return false;
        }
        try {
            JsonNode root = ObjectMapperFactory.createObjectMapper().readTree(json);
            if (root == null || root.isMissingNode()) {
                return false;
            }
            boolean changed = generalizeJsonNode(root);
            if (!changed) {
                return false;
            }
            String generalised = ObjectMapperFactory.createObjectMapper().writeValueAsString(root);
            request.withBody(new JsonBody(generalised, null, MediaType.APPLICATION_JSON, JsonBody.DEFAULT_MATCH_TYPE));
            return true;
        } catch (Throwable ignore) {
            // Malformed or unparseable JSON — keep the body verbatim.
            return false;
        }
    }

    /**
     * Recursively replace volatile string leaves with json-unit regex
     * placeholders. Numeric/boolean/null leaves are left untouched: generalising
     * them is harder to do safely and over-generalising stable numbers (counts,
     * ids that are also primary keys we may want to assert on) is the riskier
     * direction. Returns true when at least one leaf was changed.
     */
    private static boolean generalizeJsonNode(JsonNode node) {
        boolean changed = false;
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<String> names = object.fieldNames();
            List<String> fieldNames = new ArrayList<>();
            while (names.hasNext()) {
                fieldNames.add(names.next());
            }
            for (String field : fieldNames) {
                JsonNode child = object.get(field);
                if (child.isTextual()) {
                    String generalised = generalizedTextLeaf(child.textValue());
                    if (generalised != null) {
                        object.put(field, generalised);
                        changed = true;
                    }
                } else {
                    changed |= generalizeJsonNode(child);
                }
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                JsonNode child = node.get(i);
                if (child.isTextual()) {
                    String generalised = generalizedTextLeaf(child.textValue());
                    if (generalised != null) {
                        ((com.fasterxml.jackson.databind.node.ArrayNode) node).set(i, com.fasterxml.jackson.databind.node.TextNode.valueOf(generalised));
                        changed = true;
                    }
                } else {
                    changed |= generalizeJsonNode(child);
                }
            }
        }
        return changed;
    }

    /**
     * If the text value looks volatile, return its json-unit regex placeholder
     * form ({@code ${json-unit.regex}<quoted-pattern>}); otherwise return null so
     * the caller leaves the leaf untouched.
     */
    private static String generalizedTextLeaf(String value) {
        if (!isVolatileValue(value)) {
            return null;
        }
        // Match any value of the same broad shape; quote nothing fancy — a permissive
        // ".+" keeps the body reusable while still asserting the field is present.
        return JSON_UNIT_REGEX_PREFIX + ANY_VALUE_REGEX;
    }

    private static String jsonStringBody(Body<?> body) {
        if (body.getType() != Body.Type.JSON && body.getType() != Body.Type.STRING) {
            return null;
        }
        Object value = body.getValue();
        if (!(value instanceof String)) {
            return null;
        }
        String text = ((String) value).trim();
        if (text.isEmpty() || (text.charAt(0) != '{' && text.charAt(0) != '[')) {
            return null;
        }
        return text;
    }

    /**
     * The single, conservative volatility test shared by query params, headers
     * and body leaves. A value is volatile when it is a UUID, a long numeric id,
     * an ISO-8601 or epoch-millis timestamp, a JWT, or a long opaque token.
     */
    static boolean isVolatileValue(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        // Strip a leading scheme (e.g. "Bearer ", "Basic ") so the credential body
        // is what we test against the token heuristics.
        String token = trimmed;
        int space = trimmed.indexOf(' ');
        if (space > 0 && space < trimmed.length() - 1) {
            String scheme = trimmed.substring(0, space).toLowerCase();
            if (scheme.equals("bearer") || scheme.equals("basic") || scheme.equals("digest")) {
                token = trimmed.substring(space + 1).trim();
            }
        }
        return UUID_VALUE.matcher(token).matches()
            || JWT.matcher(token).matches()
            || EPOCH_MILLIS.matcher(token).matches()
            || LONG_NUMERIC_ID.matcher(token).matches()
            || ISO_8601.matcher(token).matches()
            || isOpaqueToken(token);
    }

    /**
     * A long opaque token is generalised only when it has the high-entropy,
     * mixed-content shape of a credential/session token: it must match
     * {@link #OPAQUE_TOKEN} <i>and</i> contain at least one lowercase letter, or
     * mix letters with digits. This keeps stable all-uppercase codes and
     * {@code SCREAMING_SNAKE_CASE} enum constants (which contain no lowercase and
     * usually no digits) verbatim, honouring the conservative contract.
     */
    private static boolean isOpaqueToken(String token) {
        if (!OPAQUE_TOKEN.matcher(token).matches()) {
            return false;
        }
        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c >= 'a' && c <= 'z') {
                hasLower = true;
            } else if (c >= 'A' && c <= 'Z') {
                hasUpper = true;
            } else if (c >= '0' && c <= '9') {
                hasDigit = true;
            }
        }
        // lowercase present => clearly not an all-caps enum/code; OR letters mixed
        // with digits => high-entropy token. All-uppercase-with-underscores (enum)
        // and all-uppercase-only (code) are excluded.
        return hasLower || ((hasUpper || hasLower) && hasDigit);
    }

    // ----- path / body helpers -----------------------------------------------

    private static String[] pathSegments(HttpRequest request) {
        String value = request.getPath() != null ? request.getPath().getValue() : "";
        if (value == null) {
            value = "";
        }
        // split keeps leading empty element for a leading slash; we normalise in callers
        return value.split("/", -1);
    }

    private static boolean endsWithSlash(HttpRequest request) {
        String value = request.getPath() != null ? request.getPath().getValue() : "";
        return value != null && value.length() > 1 && value.endsWith("/");
    }

    private static List<String> trimLeadingEmpty(List<String> segments) {
        List<String> copy = new ArrayList<>(segments);
        if (!copy.isEmpty() && copy.get(0).isEmpty()) {
            copy.remove(0);
        }
        // drop a trailing empty element produced by a trailing slash; the slash
        // is re-added by the caller based on the original path
        if (!copy.isEmpty() && copy.get(copy.size() - 1).isEmpty()) {
            copy.remove(copy.size() - 1);
        }
        return copy;
    }

    private static boolean isVariableSegment(String segment) {
        if (segment == null || segment.isEmpty()) {
            return false;
        }
        return NUMERIC_ID.matcher(segment).matches() || UUID_ID.matcher(segment).matches();
    }

    private static String parameterName(int index) {
        return index == 0 ? "id" : "id" + index;
    }

    private static String bodyShape(HttpRequest request) {
        Body<?> body = request.getBody();
        if (body == null) {
            return "<none>";
        }
        Object value = body.getValue();
        return body.getType() + ":" + (value == null ? "" : value);
    }

    private static String nullableValue(NottableString value) {
        if (value == null) {
            return "";
        }
        return (value.isNot() ? "!" : "") + value.getValue();
    }
}
