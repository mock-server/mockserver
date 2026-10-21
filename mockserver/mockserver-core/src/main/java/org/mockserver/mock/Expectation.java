package org.mockserver.mock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;
import org.mockserver.time.TimeService;
import org.mockserver.uuid.UUIDService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockserver.model.OpenAPIDefinition.openAPI;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public class Expectation extends ObjectWithJsonToString {

    private static final String[] excludedFields = {"id", "created", "sortableExpectationId"};
    /**
     * Request header (0-based) that lets a caller force which entry of a multi-response
     * {@code httpResponses} sequence is served for that single request — overriding the
     * configured {@link ResponseMode} without advancing the rotation position (peek
     * semantics; see {@link #getPrimaryAction(Integer)} and {@link #consumeMatch(Integer)}).
     * Invalid or out-of-bounds values are ignored (normal selection applies). The header is
     * NOT stripped from the request model: it is retained in recordings and on the logged
     * request, and matching is unaffected. It is filtered only from outbound forwards —
     * HTTP/proxy forwards via {@code MockServerHttpRequestToFullHttpRequest.setHeader} and the
     * WebSocket passthrough via {@code WebSocketProxyRelayHandler.isSuppressedRelayHeader} —
     * so it never leaks upstream.
     */
    public static final String FORCE_RESPONSE_INDEX_HEADER = "x-mockserver-response-index";
    private static final AtomicInteger EXPECTATION_COUNTER = new AtomicInteger(0);
    private static final long START_TIME = System.currentTimeMillis();
    private int hashCode;
    private String id;
    @JsonIgnore
    private long created;
    private int priority;
    private Integer percentage;
    private HttpChaosProfile chaos;
    private RateLimit rateLimit;
    private SortableExpectationId sortableExpectationId;
    private final RequestDefinition httpRequest;
    private final Times times;
    private final TimeToLive timeToLive;
    private HttpResponse httpResponse;
    private HttpTemplate httpResponseTemplate;
    private HttpClassCallback httpResponseClassCallback;
    private HttpObjectCallback httpResponseObjectCallback;
    private HttpForward httpForward;
    private HttpTemplate httpForwardTemplate;
    private HttpClassCallback httpForwardClassCallback;
    private HttpObjectCallback httpForwardObjectCallback;
    private HttpOverrideForwardedRequest httpOverrideForwardedRequest;
    private HttpForwardValidateAction httpForwardValidateAction;
    private HttpForwardWithFallback httpForwardWithFallback;
    private HttpSseResponse httpSseResponse;
    private HttpLlmResponse httpLlmResponse;
    private HttpWebSocketResponse httpWebSocketResponse;
    private GrpcStreamResponse grpcStreamResponse;
    private GrpcBidiResponse grpcBidiResponse;
    private BinaryResponse binaryResponse;
    private DnsResponse dnsResponse;
    private HttpError httpError;
    private List<AfterAction> beforeActions;
    private List<AfterAction> afterActions;
    private List<ExpectationStep> steps;
    private List<HttpResponse> httpResponses;
    private ResponseMode responseMode;
    private List<Integer> responseWeights;
    private Integer switchAfter;
    private List<CrossProtocolScenario> crossProtocolScenarios;
    private List<CaptureRule> capture;
    private String namespace;
    // read only by control-plane filters and serialisation, never by matching; always null or unmodifiable
    private Map<String, Object> metadata;
    // metadata serialised once per change, so writing the expectation again (persistence) copies it rather than re-serialising
    private transient String metadataJson;
    private String scenarioName;
    private String scenarioState;
    private String newScenarioState;
    @JsonIgnore
    private final AtomicInteger matchCount = new AtomicInteger(0);
    /**
     * Dedicated rotation position for {@code SEQUENTIAL}/{@code SWITCH} response-sequence selection.
     * A NORMAL request advances this counter and snapshots it per-thread at match time (exactly as
     * {@link #matchCount} historically did); a request served a forced variant via
     * {@link #FORCE_RESPONSE_INDEX_HEADER} (peek semantics) still consumes a {@code Times} unit and
     * increments {@link #matchCount}, but does NOT touch this counter — so a forced request is
     * transparent to the rotation seen by concurrent normal callers. Keeping the rotation on its own
     * counter (advanced only by normal matches, snapshotted at match time) preserves the collision-free
     * invariant unconditionally, without a resolution-time compensation read that could race.
     */
    @JsonIgnore
    private final AtomicInteger rotationCount = new AtomicInteger(0);
    @JsonIgnore
    private final AtomicLong chaosFirstMatchEpochMillis = new AtomicLong(0L);
    /**
     * Per-thread snapshot of {@link #rotationCount} taken at match time (in {@link #consumeMatch}),
     * read by {@link #selectFromResponses()} so selection uses the position fixed when this request
     * matched rather than a later shared read.
     */
    @JsonIgnore
    private final ThreadLocal<Integer> lastRotationSnapshot = new ThreadLocal<>();

    // Memoized once so the byte-budget weight is identical at add-time and evict-time; -1 means unset.
    @JsonIgnore
    private transient long estimatedHeapSize = -1;

    // Fixed overheads for the byte-budget estimate; see estimatedHeapSize().
    private static final long BASE_EXPECTATION_OVERHEAD_BYTES = 512;
    private static final long PER_HTTP_MESSAGE_OVERHEAD_BYTES = 1152;
    private static final long HEADER_ENTRY_OVERHEAD_BYTES = 64;
    // A JSON request body is parsed once into a JsonNode tree (JsonStringMatcher.matcherJsonNode) held
    // for the expectation's life — the dominant heap term, measured at ~12-25x the raw JSON. Counted
    // from the STABLE raw bytes so the weight does not change if/when the tree is later materialised;
    // the multiplier errs to the high end so the estimate over-counts (evicts early) rather than under.
    private static final long JSON_MATCHER_TREE_EXPANSION = 20;
    // metadata is held as a Map/List/boxed-value graph; over-count its JSON text as for a parsed JSON body
    private static final long METADATA_EXPANSION = JSON_MATCHER_TREE_EXPANSION;

    /**
     * Specify the OpenAPI and operationId to match against by URL or payload and string as follows:
     * <p><pre>
     *   // Create from a publicly hosted HTTP location (json or yaml)
     *   when("https://raw.githubusercontent.com/OAI/OpenAPI-Specification/master/examples/v3.0/petstore-expanded.yaml", "showPetById")
     *
     *   // Create from a file on the local filesystem (json or yaml)
     *   when("file://Users/myuser/git/mockserver/mockserver-core/src/test/resources/org/mockserver/openapi/openapi_petstore_example.json", "showPetById");
     *
     *   // Create from a classpath resource in the /api package (json or yaml)
     *   when("org/mockserver/openapi/openapi_petstore_example.json", "showPetById");
     *
     *   // Create from an OpenAPI payload (json or yaml)
     *   when("{\"openapi\": \"3.0.0\", \"info\": { ...", "showPetById")
     * </pre><p>
     *
     * @param specUrlOrPayload the OpenAPI to match against by URL or payload
     * @param operationId      operationId from the OpenAPI to match against i.e. "showPetById"
     * @return the Expectation
     */
    public static Expectation when(String specUrlOrPayload, String operationId) {
        return new Expectation(openAPI(specUrlOrPayload, operationId));
    }

    /**
     * Specify the OpenAPI and operationId to match against by URL or payload and string with a match priority as follows:
     * <p><pre>
     *   // Create from a publicly hosted HTTP location (json or yaml)
     *   when("https://raw.githubusercontent.com/OAI/OpenAPI-Specification/master/examples/v3.0/petstore-expanded.yaml", "showPetById", 10)
     *
     *   // Create from a file on the local filesystem (json or yaml)
     *   when("file://Users/myuser/git/mockserver/mockserver-core/src/test/resources/org/mockserver/openapi/openapi_petstore_example.json", "showPetById", 10);
     *
     *   // Create from a classpath resource in the /api package (json or yaml)
     *   when("org/mockserver/openapi/openapi_petstore_example.json", "showPetById", 10);
     *
     *   // Create from an OpenAPI payload (json or yaml)
     *   when("{\"openapi\": \"3.0.0\", \"info\": { ...", "showPetById", 10)
     * </pre><p>
     *
     * @param specUrlOrPayload the OpenAPI to match against by URL or payload
     * @param operationId      operationId from the OpenAPI to match against i.e. "showPetById"
     * @param priority         the priority with which this expectation is used to match requests compared to other expectations (high first)
     * @return the Expectation
     */
    public static Expectation when(String specUrlOrPayload, String operationId, int priority) {
        return new Expectation(openAPI(specUrlOrPayload, operationId), Times.unlimited(), TimeToLive.unlimited(), priority);
    }

    /**
     * Specify the OpenAPI and operationId to match against by URL or payload and string for a limit number of times or time as follows:
     * <p><pre>
     *   // Create from a publicly hosted HTTP location (json or yaml)
     *   when("https://raw.githubusercontent.com/OAI/OpenAPI-Specification/master/examples/v3.0/petstore-expanded.yaml", "showPetById", 5, exactly(TimeUnit.SECONDS, 90))
     *
     *   // Create from a file on the local filesystem (json or yaml)
     *   when("file://Users/myuser/git/mockserver/mockserver-core/src/test/resources/org/mockserver/openapi/openapi_petstore_example.json", "showPetById", 5, exactly(TimeUnit.SECONDS, 90));
     *
     *   // Create from a classpath resource in the /api package (json or yaml)
     *   when("org/mockserver/openapi/openapi_petstore_example.json", "showPetById", 5, exactly(TimeUnit.SECONDS, 90));
     *
     *   // Create from an OpenAPI payload (json or yaml)
     *   when("{\"openapi\": \"3.0.0\", \"info\": { ...", "showPetById", 5, exactly(TimeUnit.SECONDS, 90))
     * </pre><p>
     *
     * @param specUrlOrPayload the OpenAPI to match against by URL or payload
     * @param operationId      operationId from the OpenAPI to match against i.e. "showPetById"
     * @param times            the number of times to use this expectation to match requests
     * @param timeToLive       the time this expectation should be used to match requests
     * @return the Expectation
     */
    public static Expectation when(String specUrlOrPayload, String operationId, Times times, TimeToLive timeToLive) {
        return new Expectation(openAPI(specUrlOrPayload, operationId), times, timeToLive, 0);
    }

    /**
     * Specify the OpenAPI and operationId to match against by URL or payload and string for a limit number of times or time and a match priority as follows:
     * <p><pre>
     *   // Create from a publicly hosted HTTP location (json or yaml)
     *   when("https://raw.githubusercontent.com/OAI/OpenAPI-Specification/master/examples/v3.0/petstore-expanded.yaml", "showPetById", 5, exactly(TimeUnit.SECONDS, 90))
     *
     *   // Create from a file on the local filesystem (json or yaml)
     *   when("file://Users/myuser/git/mockserver/mockserver-core/src/test/resources/org/mockserver/openapi/openapi_petstore_example.json", "showPetById", 5, exactly(TimeUnit.SECONDS, 90));
     *
     *   // Create from a classpath resource in the /api package (json or yaml)
     *   when("org/mockserver/openapi/openapi_petstore_example.json", "showPetById", 5, exactly(TimeUnit.SECONDS, 90));
     *
     *   // Create from an OpenAPI payload (json or yaml)
     *   when("{\"openapi\": \"3.0.0\", \"info\": { ...", "showPetById", 5, exactly(TimeUnit.SECONDS, 90))
     * </pre><p>
     *
     * @param specUrlOrPayload the OpenAPI to match against by URL or payload
     * @param operationId      operationId from the OpenAPI to match against i.e. "showPetById"
     * @param times            the number of times to use this expectation to match requests
     * @param timeToLive       the time this expectation should be used to match requests
     * @param priority         the priority with which this expectation is used to match requests compared to other expectations (high first)
     * @return the Expectation
     */
    public static Expectation when(String specUrlOrPayload, String operationId, Times times, TimeToLive timeToLive, int priority) {
        return new Expectation(openAPI(specUrlOrPayload, operationId), times, timeToLive, priority);
    }

    /**
     * Specify the HttpRequest to match against as follows:
     * <p><pre>
     *     when(
     *         request()
     *             .withMethod("GET")
     *             .withPath("/some/path")
     *     ).thenRespond(
     *         response()
     *             .withContentType(APPLICATION_JSON_UTF_8)
     *             .withBody("{\"some\": \"body\"}")
     *     );
     * </pre><p>
     *
     * @param httpRequest the HttpRequest to match against
     * @return the Expectation
     */
    public static Expectation when(HttpRequest httpRequest) {
        return new Expectation(httpRequest);
    }

    /**
     * Specify the HttpRequest to match against with a match priority as follows:
     * <p><pre>
     *     when(
     *         request()
     *             .withMethod("GET")
     *             .withPath("/some/path"),
     *         10
     *     ).thenRespond(
     *         response()
     *             .withContentType(APPLICATION_JSON_UTF_8)
     *             .withBody("{\"some\": \"body\"}")
     *     );
     * </pre><p>
     *
     * @param httpRequest the HttpRequest to match against
     * @param priority    the priority with which this expectation is used to match requests compared to other expectations (high first)
     * @return the Expectation
     */
    public static Expectation when(HttpRequest httpRequest, int priority) {
        return new Expectation(httpRequest, Times.unlimited(), TimeToLive.unlimited(), priority);
    }

    /**
     * Specify the HttpRequest to match against for a limit number of times or time as follows:
     * <p><pre>
     *     when(
     *         request()
     *             .withMethod("GET")
     *             .withPath("/some/path"),
     *         5,
     *         exactly(TimeUnit.SECONDS, 90)
     *     ).thenRespond(
     *         response()
     *             .withContentType(APPLICATION_JSON_UTF_8)
     *             .withBody("{\"some\": \"body\"}")
     *     );
     * </pre><p>
     *
     * @param httpRequest the HttpRequest to match against
     * @param times       the number of times to use this expectation to match requests
     * @param timeToLive  the time this expectation should be used to match requests
     * @return the Expectation
     */
    public static Expectation when(HttpRequest httpRequest, Times times, TimeToLive timeToLive) {
        return new Expectation(httpRequest, times, timeToLive, 0);
    }

    /**
     * Specify the HttpRequest to match against for a limit number of times or time and a match priority as follows:
     * <p><pre>
     *     when(
     *         request()
     *             .withMethod("GET")
     *             .withPath("/some/path"),
     *         5,
     *         exactly(TimeUnit.SECONDS, 90),
     *         10
     *     ).thenRespond(
     *         response()
     *             .withContentType(APPLICATION_JSON_UTF_8)
     *             .withBody("{\"some\": \"body\"}")
     *     );
     * </pre><p>
     *
     * @param httpRequest the HttpRequest to match against
     * @param times       the number of times to use this expectation to match requests
     * @param timeToLive  the time this expectation should be used to match requests
     * @param priority    the priority with which this expectation is used to match requests compared to other expectations (high first)
     * @return the Expectation
     */
    public static Expectation when(HttpRequest httpRequest, Times times, TimeToLive timeToLive, int priority) {
        return new Expectation(httpRequest, times, timeToLive, priority);
    }

    public Expectation(RequestDefinition requestDefinition) {
        this(requestDefinition, Times.unlimited(), TimeToLive.unlimited(), 0);
    }

    public Expectation(RequestDefinition requestDefinition, Times times, TimeToLive timeToLive, int priority) {
        // ensure created enforces insertion order by relying on system time, and a counter
        EXPECTATION_COUNTER.compareAndSet(Integer.MAX_VALUE, 0);
        this.created = System.currentTimeMillis() - START_TIME + EXPECTATION_COUNTER.incrementAndGet();
        this.httpRequest = requestDefinition;
        this.times = times;
        this.timeToLive = timeToLive;
        this.priority = priority;
    }

    /**
     * <p>
     * Set id of this expectation which can be used to update this expectation
     * later or for clearing or verifying by expectation id.
     * </p>
     * <p>
     * Note: Each unique expectation must have a unique id otherwise this
     * expectation will update a existing expectation with the same id.
     * </p>
     * @param id unique string for expectation's id
     */
    public Expectation withId(String id) {
        this.id = id;
        this.sortableExpectationId = null;
        return this;
    }

    public Expectation withIdIfNull(String id) {
        if (this.id == null) {
            this.id = id;
            this.sortableExpectationId = null;
        }
        return this;
    }

    /**
     * @return whether an id has been set, without assigning one as {@link #getId()} does
     */
    @JsonIgnore
    public boolean hasId() {
        return id != null;
    }

    public String getId() {
        if (id == null) {
            withId(UUIDService.getUUID());
        }
        return id;
    }

    /**
     * <p>
     * Set priority of this expectation which is used to determine the matching
     * order of expectations when a request is received.
     * </p>
     * <p>
     * Matching is ordered by priority (highest first) then creation (earliest first).
     * </p>
     * @param priority expectation's priority
     */
    public Expectation withPriority(int priority) {
        this.priority = priority;
        this.sortableExpectationId = null;
        return this;
    }

    public int getPriority() {
        return priority;
    }

    public Expectation withPercentage(Integer percentage) {
        if (percentage != null && (percentage < 0 || percentage > 100)) {
            throw new IllegalArgumentException("percentage must be between 0 and 100");
        }
        this.percentage = percentage;
        this.hashCode = 0;
        return this;
    }

    public Integer getPercentage() {
        return percentage;
    }

    public Expectation withChaos(HttpChaosProfile chaos) {
        this.chaos = chaos;
        this.hashCode = 0;
        return this;
    }

    public HttpChaosProfile getChaos() {
        return chaos;
    }

    public Expectation withRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
        this.hashCode = 0;
        return this;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    @JsonIgnore
    public boolean matchesByPercentage() {
        if (percentage == null || percentage == 100) {
            return true;
        }
        if (percentage == 0) {
            return false;
        }
        return ThreadLocalRandom.current().nextInt(100) < percentage;
    }

    public Expectation withCreated(long created) {
        this.created = created;
        this.sortableExpectationId = null;
        this.hashCode = 0;
        return this;
    }

    public long getCreated() {
        return created;
    }

    @JsonIgnore
    public SortableExpectationId getSortableId() {
        if (sortableExpectationId == null) {
            sortableExpectationId = new SortableExpectationId(getId(), priority, created);
        }
        return sortableExpectationId;
    }

    public RequestDefinition getHttpRequest() {
        return httpRequest;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    public HttpTemplate getHttpResponseTemplate() {
        return httpResponseTemplate;
    }

    public HttpClassCallback getHttpResponseClassCallback() {
        return httpResponseClassCallback;
    }

    public HttpObjectCallback getHttpResponseObjectCallback() {
        return httpResponseObjectCallback;
    }

    public HttpForward getHttpForward() {
        return httpForward;
    }

    public HttpTemplate getHttpForwardTemplate() {
        return httpForwardTemplate;
    }

    public HttpClassCallback getHttpForwardClassCallback() {
        return httpForwardClassCallback;
    }

    public HttpObjectCallback getHttpForwardObjectCallback() {
        return httpForwardObjectCallback;
    }

    public HttpOverrideForwardedRequest getHttpOverrideForwardedRequest() {
        return httpOverrideForwardedRequest;
    }

    public HttpForwardValidateAction getHttpForwardValidateAction() {
        return httpForwardValidateAction;
    }

    public HttpForwardWithFallback getHttpForwardWithFallback() {
        return httpForwardWithFallback;
    }

    public HttpSseResponse getHttpSseResponse() {
        return httpSseResponse;
    }

    public HttpLlmResponse getHttpLlmResponse() {
        return httpLlmResponse;
    }

    public HttpWebSocketResponse getHttpWebSocketResponse() {
        return httpWebSocketResponse;
    }

    public GrpcStreamResponse getGrpcStreamResponse() {
        return grpcStreamResponse;
    }

    public GrpcBidiResponse getGrpcBidiResponse() {
        return grpcBidiResponse;
    }

    public BinaryResponse getBinaryResponse() {
        return binaryResponse;
    }

    public DnsResponse getDnsResponse() {
        return dnsResponse;
    }

    public HttpError getHttpError() {
        return httpError;
    }

    public List<AfterAction> getBeforeActions() {
        return beforeActions != null ? Collections.unmodifiableList(beforeActions) : null;
    }

    public List<AfterAction> getAfterActions() {
        return afterActions != null ? Collections.unmodifiableList(afterActions) : null;
    }

    public List<ExpectationStep> getSteps() {
        return steps != null ? Collections.unmodifiableList(steps) : null;
    }

    public Expectation withSteps(ExpectationStep... steps) {
        if (steps != null && steps.length > 0) {
            this.steps = new ArrayList<>(Arrays.asList(steps));
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation withSteps(List<ExpectationStep> steps) {
        if (steps != null && !steps.isEmpty()) {
            this.steps = new ArrayList<>(steps);
            this.hashCode = 0;
        }
        return this;
    }

    /**
     * Validates the steps list for legal combinations:
     * <ul>
     *   <li>Exactly one step must be marked as the responder ({@code responder = true}).</li>
     *   <li>{@code httpError} cannot be combined with other steps (it must be the only step).</li>
     *   <li>Each step must have exactly one action target set.</li>
     *   <li>{@code steps} cannot be combined with {@code beforeActions} — use steps for the full ordered pipeline.</li>
     *   <li>{@code steps} cannot be combined with a top-level primary response action — the responder step defines the action.</li>
     *   <li>{@code steps} MAY coexist with {@code afterActions} — after-actions fire after the steps pipeline completes.</li>
     * </ul>
     *
     * @return null if valid, or an error message describing the violation
     */
    @JsonIgnore
    public String validateSteps() {
        if (steps == null || steps.isEmpty()) {
            return null;
        }

        // Coexistence checks: steps is the unified ordered pipeline and must be self-contained
        if (beforeActions != null && !beforeActions.isEmpty()) {
            return "steps cannot be combined with beforeActions - use steps for the full ordered pipeline";
        }
        if (hasTopLevelPrimaryAction()) {
            return "steps cannot be combined with a top-level response action - the responder step defines the action";
        }

        int responderCount = 0;
        boolean hasHttpError = false;
        for (int i = 0; i < steps.size(); i++) {
            ExpectationStep step = steps.get(i);

            // count non-null targets
            int targetCount = 0;
            if (step.getHttpRequest() != null) {
                targetCount++;
            }
            if (step.getHttpClassCallback() != null) {
                targetCount++;
            }
            if (step.getHttpObjectCallback() != null) {
                targetCount++;
            }
            if (step.getHttpForward() != null) {
                targetCount++;
            }
            if (step.getHttpOverrideForwardedRequest() != null) {
                targetCount++;
            }
            if (step.getHttpResponse() != null) {
                targetCount++;
            }
            if (step.getHttpError() != null) {
                targetCount++;
            }
            if (targetCount == 0) {
                return "step[" + i + "] has no action target set";
            }
            if (targetCount > 1) {
                return "step[" + i + "] has multiple action targets set; each step must have exactly one";
            }

            if (step.getHttpError() != null) {
                hasHttpError = true;
            }
            if (Boolean.TRUE.equals(step.getResponder())) {
                responderCount++;
                // non-responder-eligible targets used as responder
                if (step.getHttpRequest() != null) {
                    return "step[" + i + "] is marked as responder but uses httpRequest (webhook); webhooks are side-effect-only and cannot produce a response";
                }
            }
        }

        if (responderCount == 0) {
            return "steps must contain exactly one step with responder=true, but none found";
        }
        if (responderCount > 1) {
            return "steps must contain exactly one step with responder=true, but found " + responderCount;
        }
        if (hasHttpError && steps.size() > 1) {
            return "httpError cannot be combined with other steps; it must be the only step";
        }

        return null;
    }

    /**
     * Returns {@code true} if any top-level primary response/forward/error action is set
     * on this expectation (the traditional action fields, as opposed to actions inside steps).
     */
    @JsonIgnore
    private boolean hasTopLevelPrimaryAction() {
        return httpResponse != null
            || httpResponseTemplate != null
            || httpResponseClassCallback != null
            || httpResponseObjectCallback != null
            || httpForward != null
            || httpForwardTemplate != null
            || httpForwardClassCallback != null
            || httpForwardObjectCallback != null
            || httpOverrideForwardedRequest != null
            || httpForwardValidateAction != null
            || httpForwardWithFallback != null
            || httpSseResponse != null
            || httpLlmResponse != null
            || httpWebSocketResponse != null
            || grpcStreamResponse != null
            || grpcBidiResponse != null
            || binaryResponse != null
            || dnsResponse != null
            || httpError != null
            || (httpResponses != null && !httpResponses.isEmpty());
    }

    public List<HttpResponse> getHttpResponses() {
        return httpResponses != null ? Collections.unmodifiableList(httpResponses) : null;
    }

    /**
     * Stable, memoized estimate of the heap this expectation retains, used as the weight for the
     * expectation store's byte budget ({@code maxExpectationsSizeInBytes}). Counts request/response body
     * bytes, header characters, inline OpenAPI spec and template text, plus fixed overheads. For a JSON
     * request body it also adds an estimate of the JsonNode tree the matcher parses it into
     * ({@code JsonStringMatcher.matcherJsonNode}) — the dominant retained term — as
     * {@code rawBytes * JSON_MATCHER_TREE_EXPANSION}.
     * <p>
     * That tree is materialised LAZILY (on first match), possibly after this expectation is admitted.
     * Estimating it from the raw bytes and body type — both fixed when the expectation is built — rather
     * than from the maybe-not-yet-parsed tree keeps the weight identical at add-time and evict-time, the
     * invariant the running byte total depends on, and never forces a parse (so it changes neither
     * behaviour nor cost). Response bodies are counted as raw bytes only (served, not parsed into a
     * retained matcher tree).
     */
    @JsonIgnore
    public long estimatedHeapSize() {
        if (estimatedHeapSize < 0) {
            long size = BASE_EXPECTATION_OVERHEAD_BYTES + requestDefinitionBytes(this.httpRequest, true);
            size += responseBytes(httpResponse);
            if (httpResponses != null) {
                for (HttpResponse response : httpResponses) {
                    size += responseBytes(response);
                }
            }
            if (httpResponseTemplate != null && httpResponseTemplate.getTemplate() != null) {
                size += httpResponseTemplate.getTemplate().length();
            }
            if (httpForwardTemplate != null && httpForwardTemplate.getTemplate() != null) {
                size += httpForwardTemplate.getTemplate().length();
            }
            if (metadata != null) {
                size += metadataJsonLength() * METADATA_EXPANSION;
            }
            estimatedHeapSize = size;
        }
        return estimatedHeapSize;
    }

    /**
     * Estimated heap retained by a request definition on its own, once no matcher holds a parsed tree
     * of its body (the definition of a removed expectation). Same basis as {@link #estimatedHeapSize()}
     * without the matcher-tree term, plus the body's cached decoded String when it holds one (as the
     * event-log weigher charges it) — the definition may still be shared, so it is weighed, not released.
     */
    public static long estimatedRequestDefinitionHeapSize(RequestDefinition request) {
        if (request == null) {
            return 0;
        }
        long size = requestDefinitionBytes(request, false);
        if (request instanceof HttpRequest && ((HttpRequest) request).getBody() != null) {
            size += ((HttpRequest) request).getBody().retainedDerivedFormBytes();
        }
        return Math.max(PER_HTTP_MESSAGE_OVERHEAD_BYTES, size);
    }

    private static long requestDefinitionBytes(RequestDefinition request, boolean includeMatcherTree) {
        long size = 0;
        if (request instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) request;
            size += PER_HTTP_MESSAGE_OVERHEAD_BYTES;
            byte[] b = httpRequest.getBodyAsRawBytes();
            long bodyBytes = b != null ? b.length : 0;
            size += bodyBytes;
            if (includeMatcherTree && httpRequest.getBody() instanceof JsonBody) {
                size += bodyBytes * JSON_MATCHER_TREE_EXPANSION;
            }
            size += headerBytes(httpRequest.getHeaders());
        } else if (request instanceof OpenAPIDefinition) {
            size += PER_HTTP_MESSAGE_OVERHEAD_BYTES;
            String spec = ((OpenAPIDefinition) request).getSpecUrlOrPayload();
            if (spec != null) {
                size += spec.length();
            }
        }
        return size;
    }

    private static long responseBytes(HttpResponse response) {
        if (response == null) {
            return 0;
        }
        long size = PER_HTTP_MESSAGE_OVERHEAD_BYTES;
        byte[] b = response.getBodyAsRawBytes();
        if (b != null) {
            size += b.length;
        }
        size += headerBytes(response.getHeaders());
        return size;
    }

    private static long headerBytes(Headers headers) {
        if (headers == null || headers.isEmpty()) {
            return 0;
        }
        long size = 0;
        com.google.common.collect.Multimap<NottableString, NottableString> multimap = headers.getMultimap();
        for (java.util.Map.Entry<NottableString, NottableString> entry : multimap.entries()) {
            size += HEADER_ENTRY_OVERHEAD_BYTES;
            NottableString key = entry.getKey();
            if (key != null && key.getValue() != null) {
                size += key.getValue().length();
            }
            NottableString value = entry.getValue();
            if (value != null && value.getValue() != null) {
                size += value.getValue().length();
            }
        }
        return size;
    }

    public Expectation thenRespond(List<HttpResponse> httpResponses) {
        if (httpResponses != null && !httpResponses.isEmpty()) {
            httpResponses.forEach(Expectation::rejectResponseMatchingBody);
            this.httpResponses = new ArrayList<>(httpResponses);
            this.hashCode = 0;
        }
        return this;
    }

    public ResponseMode getResponseMode() {
        return responseMode;
    }

    public Expectation withResponseMode(ResponseMode responseMode) {
        this.responseMode = responseMode;
        this.hashCode = 0;
        return this;
    }

    public List<Integer> getResponseWeights() {
        return responseWeights != null ? Collections.unmodifiableList(responseWeights) : null;
    }

    /**
     * Specify the relative weight of each response in {@code httpResponses} (index-aligned), used when
     * {@link ResponseMode#WEIGHTED} selection is active. A missing or {@code null} weight defaults to 1.
     * Weights are ignored unless the response mode is {@code WEIGHTED}.
     */
    public Expectation withResponseWeights(List<Integer> responseWeights) {
        if (responseWeights != null && !responseWeights.isEmpty()) {
            this.responseWeights = new ArrayList<>(responseWeights);
            this.hashCode = 0;
        }
        return this;
    }

    public Integer getSwitchAfter() {
        return switchAfter;
    }

    /**
     * Lightweight per-expectation hit-count branching, used when {@link ResponseMode#SWITCH} selection is active.
     * <p>
     * With an index-aligned {@code httpResponses} list and a positive {@code switchAfter} of {@code N}, the
     * expectation serves the first response for its first {@code N} matches, then advances one index for every
     * further block of {@code N} matches, clamping at the last response. The common case — a list of two
     * responses — therefore serves the first response for {@code N} calls and the second response for every
     * call after that, letting a single expectation "respond differently after the Nth call" without a full
     * scenario. A {@code null} or non-positive {@code switchAfter} (or a response mode other than {@code SWITCH})
     * leaves behaviour unchanged.
     *
     * @param switchAfter the number of matches served by each response before advancing to the next (must be &gt;= 1)
     */
    public Expectation withSwitchAfter(Integer switchAfter) {
        if (switchAfter != null && switchAfter < 1) {
            throw new IllegalArgumentException("switchAfter must be greater than or equal to 1");
        }
        this.switchAfter = switchAfter;
        this.hashCode = 0;
        return this;
    }

    /**
     * Replace this expectation's before-actions with the given list; a null or empty argument leaves the
     * current list unchanged. To add one action to the existing list use {@link #withBeforeAction(AfterAction)}.
     *
     * @param beforeActions the complete list of before-actions
     */
    public Expectation withBeforeActions(AfterAction... beforeActions) {
        if (beforeActions != null && beforeActions.length > 0) {
            this.beforeActions = new ArrayList<>(Arrays.asList(beforeActions));
            this.hashCode = 0;
        }
        return this;
    }

    /**
     * Replace this expectation's before-actions with the given list; a null or empty argument leaves the
     * current list unchanged. To add one action to the existing list use {@link #withBeforeAction(AfterAction)}.
     *
     * @param beforeActions the complete list of before-actions
     */
    public Expectation withBeforeActions(List<AfterAction> beforeActions) {
        if (beforeActions != null && !beforeActions.isEmpty()) {
            this.beforeActions = new ArrayList<>(beforeActions);
            this.hashCode = 0;
        }
        return this;
    }

    /**
     * Append one before-action to this expectation's before-actions, after any already set, so chained calls
     * register every action in order; a null argument is ignored. To replace the whole list use
     * {@link #withBeforeActions(AfterAction...)}.
     *
     * @param beforeAction the before-action to append
     */
    public Expectation withBeforeAction(AfterAction beforeAction) {
        if (beforeAction != null) {
            if (this.beforeActions == null) {
                this.beforeActions = new ArrayList<>();
            }
            this.beforeActions.add(beforeAction);
            this.hashCode = 0;
        }
        return this;
    }

    /**
     * Replace this expectation's after-actions with the given list; a null or empty argument leaves the
     * current list unchanged. To add one action to the existing list use {@link #withAfterAction(AfterAction)}.
     *
     * @param afterActions the complete list of after-actions
     */
    public Expectation withAfterActions(AfterAction... afterActions) {
        if (afterActions != null && afterActions.length > 0) {
            this.afterActions = new ArrayList<>(Arrays.asList(afterActions));
            this.hashCode = 0;
        }
        return this;
    }

    /**
     * Append one after-action to this expectation's after-actions, after any already set, so chained calls
     * register every action in order; a null argument is ignored. To replace the whole list use
     * {@link #withAfterActions(AfterAction...)}.
     *
     * @param afterAction the after-action to append
     */
    public Expectation withAfterAction(AfterAction afterAction) {
        if (afterAction != null) {
            if (this.afterActions == null) {
                this.afterActions = new ArrayList<>();
            }
            this.afterActions.add(afterAction);
            this.hashCode = 0;
        }
        return this;
    }

    /**
     * Replace this expectation's after-actions with the given list; a null or empty argument leaves the
     * current list unchanged. To add one action to the existing list use {@link #withAfterAction(AfterAction)}.
     *
     * @param afterActions the complete list of after-actions
     */
    public Expectation withAfterActions(List<AfterAction> afterActions) {
        if (afterActions != null && !afterActions.isEmpty()) {
            this.afterActions = new ArrayList<>(afterActions);
            this.hashCode = 0;
        }
        return this;
    }

    /**
     * <p>
     * Set the optional namespace (a.k.a. tenant) that this expectation belongs to,
     * enabling multiple teams or test-suites to share a single MockServer instance
     * without their expectations colliding.
     * </p>
     * <p>
     * A {@code null} namespace (the default) places the expectation in the global
     * namespace, which is matched regardless of any request namespace. A non-null
     * namespace scopes the expectation so it only matches requests that carry the
     * configured namespace header ({@code matchNamespaceHeader}, default
     * {@code X-MockServer-Namespace}) with this exact value.
     * </p>
     *
     * <p>
     * The value is normalised at the source: a null, empty or whitespace-only
     * namespace is stored as {@code null} (global), so a blank namespace is
     * unambiguously "global" and can never produce an expectation that matches
     * everything yet cannot be cleared by namespace.
     * </p>
     *
     * @param namespace the namespace (tenant) for this expectation, or null for global
     */
    public Expectation withNamespace(String namespace) {
        if (namespace != null) {
            String trimmed = namespace.trim();
            namespace = trimmed.isEmpty() ? null : trimmed;
        }
        this.namespace = namespace;
        this.hashCode = 0;
        return this;
    }

    public String getNamespace() {
        return namespace;
    }

    /**
     * <p>
     * Set arbitrary metadata on this expectation, for example an owner, a ticket number or a set of tags.
     * Each value may be any JSON value: a {@code String}, {@code Number}, {@code Boolean}, {@code List},
     * {@code Map} or Jackson {@code JsonNode}. Metadata is returned when the expectation is retrieved and can
     * be used to retrieve or clear expectations, but it never affects how requests are matched.
     * </p>
     * <p>
     * The map is copied; a {@code null} or empty map removes all metadata. The copy is shallow, so do not change a
     * nested {@code List} or {@code Map} value after setting it: the serialised form is cached when first written.
     * </p>
     *
     * @param metadata the metadata for this expectation, or null for none
     */
    public Expectation withMetadata(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            this.metadata = null;
        } else {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<String, ?> entry : metadata.entrySet()) {
                copy.put(requireMetadataKey(entry.getKey()), entry.getValue());
            }
            this.metadata = Collections.unmodifiableMap(copy);
        }
        metadataChanged();
        return this;
    }

    /**
     * Add (or replace) one metadata entry, see {@link #withMetadata(Map)}.
     *
     * @param key   the metadata key, must not be blank
     * @param value any JSON value
     */
    public Expectation withMetadata(String key, Object value) {
        Map<String, Object> copy = metadata != null ? new LinkedHashMap<>(metadata) : new LinkedHashMap<>();
        copy.put(requireMetadataKey(key), value);
        this.metadata = Collections.unmodifiableMap(copy);
        metadataChanged();
        return this;
    }

    private void metadataChanged() {
        this.metadataJson = null;
        this.hashCode = 0;
        this.estimatedHeapSize = -1;
    }

    /**
     * Add (or replace) one metadata entry whose value is given as JSON text, for example
     * {@code withMetadataJson("tags", "[\"smoke\",\"checkout\"]")}; see {@link #withMetadata(Map)}.
     *
     * @param key       the metadata key, must not be blank
     * @param jsonValue any JSON value as text
     * @throws IllegalArgumentException if {@code jsonValue} is not valid JSON
     */
    public Expectation withMetadataJson(String key, String jsonValue) {
        return withMetadata(key, MetadataJson.fromJson(jsonValue));
    }

    private static String requireMetadataKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("expectation metadata keys must not be blank");
        }
        return key;
    }

    /**
     * @return this expectation's metadata (unmodifiable), or null when it has none
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * @return this expectation's metadata as compact JSON, serialised once per change, or null when it has none
     * @throws IllegalArgumentException if a metadata value cannot be serialised as JSON
     */
    @JsonIgnore
    public String getMetadataJson() {
        if (metadata == null) {
            return null;
        }
        String json = metadataJson;
        if (json == null) {
            json = MetadataJson.toJson(metadata);
            metadataJson = json;
        }
        return json;
    }

    private long metadataJsonLength() {
        try {
            return getMetadataJson().length();
        } catch (IllegalArgumentException notJson) {
            // an in-process caller's value Jackson cannot write: the budget must not fail the add
            return (long) metadata.size() * PER_HTTP_MESSAGE_OVERHEAD_BYTES;
        }
    }

    /**
     * As {@link #clone()} without the metadata: for the per-request match log entries, so a matched request
     * never serialises metadata. The full expectation, metadata included, is returned on retrieve.
     */
    public Expectation cloneWithoutMetadata() {
        Expectation clone = clone();
        clone.metadata = null;
        clone.metadataJson = null;
        return clone;
    }

    public Expectation withScenarioName(String scenarioName) {
        this.scenarioName = scenarioName;
        this.hashCode = 0;
        return this;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public Expectation withScenarioState(String scenarioState) {
        this.scenarioState = scenarioState;
        this.hashCode = 0;
        return this;
    }

    public String getScenarioState() {
        return scenarioState;
    }

    public Expectation withNewScenarioState(String newScenarioState) {
        this.newScenarioState = newScenarioState;
        this.hashCode = 0;
        return this;
    }

    public String getNewScenarioState() {
        return newScenarioState;
    }

    public List<CaptureRule> getCapture() {
        return capture != null ? Collections.unmodifiableList(capture) : null;
    }

    public Expectation withCapture(List<CaptureRule> capture) {
        if (capture != null && !capture.isEmpty()) {
            this.capture = new ArrayList<>(capture);
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation withCapture(CaptureRule... capture) {
        if (capture != null && capture.length > 0) {
            this.capture = new ArrayList<>(Arrays.asList(capture));
            this.hashCode = 0;
        }
        return this;
    }

    public List<CrossProtocolScenario> getCrossProtocolScenarios() {
        return crossProtocolScenarios;
    }

    public Expectation withCrossProtocolScenario(CrossProtocolScenario scenario) {
        if (crossProtocolScenarios == null) {
            crossProtocolScenarios = new ArrayList<>();
        }
        crossProtocolScenarios.add(scenario);
        this.hashCode = 0;
        return this;
    }

    public Expectation withCrossProtocolScenarios(List<CrossProtocolScenario> scenarios) {
        this.crossProtocolScenarios = scenarios;
        this.hashCode = 0;
        return this;
    }

    @JsonIgnore
    public Action getAction() {
        return getPrimaryAction();
    }

    @JsonIgnore
    public Action getPrimaryAction() {
        // When steps are configured, the responder step's action is the primary action
        if (steps != null && !steps.isEmpty()) {
            for (ExpectationStep step : steps) {
                if (Boolean.TRUE.equals(step.getResponder())) {
                    Action action = resolveStepAction(step);
                    if (action != null) {
                        stampExpectationId(action);
                    }
                    return action;
                }
            }
            return null;
        }
        if (httpResponses != null && !httpResponses.isEmpty()) {
            HttpResponse selected = selectFromResponses();
            if (selected != null) {
                stampExpectationId(selected);
                return selected;
            }
        }
        // Fast path: an expectation almost always has exactly one action field set, so resolve it
        // directly rather than allocating the getAllActions() list — getAction is called several times
        // per request. getAllActions() is walked only for the rare multi-action primary tie-break.
        Action first = firstConfiguredAction();
        if (first == null) {
            return null;
        }
        if (!hasMultipleConfiguredActions()) {
            stampExpectationId(first);
            return first;
        }
        Action primary = null;
        for (Action action : getAllActions()) {
            if (action.isPrimary()) {
                if (primary != null) {
                    throw new IllegalArgumentException("multiple actions marked as primary, only one action can be primary when multiple action types are configured");
                }
                primary = action;
            }
        }
        if (primary == null) {
            throw new IllegalArgumentException("when multiple action types are configured, exactly one must be marked as primary");
        }
        stampExpectationId(primary);
        return primary;
    }

    /**
     * Stamps the resolved action with this expectation's id, writing only when the value actually
     * changes. The id is stable for an expectation's serving life, so after the first stamp this is a
     * read-and-compare — removing a store to the shared {@link Action} field from every request, which
     * would otherwise invalidate that cache line across every event-loop thread serving this
     * expectation. A later {@link #withId(String)} changes the id and the next resolve re-stamps, so the
     * served action's id is always correct.
     */
    private void stampExpectationId(Action action) {
        String id = getId();
        if (!id.equals(action.getExpectationId())) {
            action.setExpectationId(id);
        }
    }

    /**
     * The first configured top-level action in precedence order, or {@code null} if none is set. The
     * field set and order MUST stay in sync with {@link #getAllActions()}.
     */
    @JsonIgnore
    private Action firstConfiguredAction() {
        if (getHttpResponse() != null && (httpResponses == null || httpResponses.isEmpty())) {
            return getHttpResponse();
        }
        if (getHttpResponseTemplate() != null) {
            return getHttpResponseTemplate();
        }
        if (getHttpResponseClassCallback() != null) {
            return getHttpResponseClassCallback();
        }
        if (getHttpResponseObjectCallback() != null) {
            return getHttpResponseObjectCallback();
        }
        if (getHttpForward() != null) {
            return getHttpForward();
        }
        if (getHttpForwardTemplate() != null) {
            return getHttpForwardTemplate();
        }
        if (getHttpForwardClassCallback() != null) {
            return getHttpForwardClassCallback();
        }
        if (getHttpForwardObjectCallback() != null) {
            return getHttpForwardObjectCallback();
        }
        if (getHttpOverrideForwardedRequest() != null) {
            return getHttpOverrideForwardedRequest();
        }
        if (getHttpForwardValidateAction() != null) {
            return getHttpForwardValidateAction();
        }
        if (getHttpForwardWithFallback() != null) {
            return getHttpForwardWithFallback();
        }
        if (getHttpSseResponse() != null) {
            return getHttpSseResponse();
        }
        if (getHttpLlmResponse() != null) {
            return getHttpLlmResponse();
        }
        if (getHttpWebSocketResponse() != null) {
            return getHttpWebSocketResponse();
        }
        if (getGrpcStreamResponse() != null) {
            return getGrpcStreamResponse();
        }
        if (getGrpcBidiResponse() != null) {
            return getGrpcBidiResponse();
        }
        if (getBinaryResponse() != null) {
            return getBinaryResponse();
        }
        if (getDnsResponse() != null) {
            return getDnsResponse();
        }
        if (getHttpError() != null) {
            return getHttpError();
        }
        return null;
    }

    /**
     * Whether more than one top-level action field is set — the only case that needs the primary
     * tie-break and the only case in which {@link #getSecondaryActions()} is non-empty. Early-exits on
     * the second action, so the common single-action path allocates nothing. The field set and order
     * MUST stay in sync with {@link #getAllActions()}.
     */
    @JsonIgnore
    private boolean hasMultipleConfiguredActions() {
        int count = 0;
        if (getHttpResponse() != null && (httpResponses == null || httpResponses.isEmpty()) && ++count > 1) {
            return true;
        }
        if (getHttpResponseTemplate() != null && ++count > 1) {
            return true;
        }
        if (getHttpResponseClassCallback() != null && ++count > 1) {
            return true;
        }
        if (getHttpResponseObjectCallback() != null && ++count > 1) {
            return true;
        }
        if (getHttpForward() != null && ++count > 1) {
            return true;
        }
        if (getHttpForwardTemplate() != null && ++count > 1) {
            return true;
        }
        if (getHttpForwardClassCallback() != null && ++count > 1) {
            return true;
        }
        if (getHttpForwardObjectCallback() != null && ++count > 1) {
            return true;
        }
        if (getHttpOverrideForwardedRequest() != null && ++count > 1) {
            return true;
        }
        if (getHttpForwardValidateAction() != null && ++count > 1) {
            return true;
        }
        if (getHttpForwardWithFallback() != null && ++count > 1) {
            return true;
        }
        if (getHttpSseResponse() != null && ++count > 1) {
            return true;
        }
        if (getHttpLlmResponse() != null && ++count > 1) {
            return true;
        }
        if (getHttpWebSocketResponse() != null && ++count > 1) {
            return true;
        }
        if (getGrpcStreamResponse() != null && ++count > 1) {
            return true;
        }
        if (getGrpcBidiResponse() != null && ++count > 1) {
            return true;
        }
        if (getBinaryResponse() != null && ++count > 1) {
            return true;
        }
        if (getDnsResponse() != null && ++count > 1) {
            return true;
        }
        if (getHttpError() != null && ++count > 1) {
            return true;
        }
        return false;
    }

    /**
     * Whether a {@code forcedResponseIndex} would actually be served as a forced variant by
     * {@link #getPrimaryAction(Integer)} — i.e. it is non-null, in-bounds, and this expectation
     * carries a multi-response {@code httpResponses} sequence with no {@code steps}. This is the
     * single source of truth for "is this a forced serve", used both at match time (to decide
     * whether the request advances the rotation counter) and at resolution time (to pick the
     * variant), so the two decisions can never diverge.
     */
    @JsonIgnore
    public boolean isForcedResponseServe(Integer forcedResponseIndex) {
        return forcedResponseIndex != null
            && hasForceableResponseSequence()
            && forcedResponseIndex >= 0 && forcedResponseIndex < httpResponses.size();
    }

    private boolean hasForceableResponseSequence() {
        return (steps == null || steps.isEmpty()) && httpResponses != null && !httpResponses.isEmpty();
    }

    /**
     * The {@link #FORCE_RESPONSE_INDEX_HEADER} index for {@code request} as this expectation would use it:
     * {@link #parseForcedResponseIndex} when it has a forceable response sequence, otherwise {@code null}
     * without reading the header. Every consumer of the index ({@link #isForcedResponseServe}) ignores it for
     * an expectation without such a sequence, so the result is indistinguishable from always parsing.
     */
    @JsonIgnore
    public Integer forcedResponseIndexFor(RequestDefinition request) {
        return hasForceableResponseSequence() ? parseForcedResponseIndex(request) : null;
    }

    /**
     * Action-resolution overload that honours a caller-forced response-sequence index (the
     * {@link #FORCE_RESPONSE_INDEX_HEADER} affordance). When the index {@link #isForcedResponseServe
     * would be served}, the response at that exact index is returned <em>without</em> reading or
     * advancing the {@link #rotationCount rotation position} (peek semantics), overriding the
     * configured {@link ResponseMode}. A null, out-of-bounds, or otherwise inapplicable index falls
     * through to the normal {@link #getPrimaryAction()} selection — a forced index is never an error.
     * The rotation is left undisturbed structurally: a forced request never increments
     * {@link #rotationCount} (see {@link #consumeMatch(Integer)}), so no resolution-time compensation
     * is needed here.
     *
     * @param forcedResponseIndex the 0-based sequence index to force, or {@code null} for normal selection
     */
    @JsonIgnore
    public Action getPrimaryAction(Integer forcedResponseIndex) {
        if (isForcedResponseServe(forcedResponseIndex)) {
            HttpResponse forced = httpResponses.get(forcedResponseIndex);
            if (forced != null) {
                forced.setExpectationId(getId());
                return forced;
            }
        }
        return getPrimaryAction();
    }

    /**
     * Convenience alias of {@link #getPrimaryAction(Integer)} mirroring the no-arg {@link #getAction()}.
     */
    @JsonIgnore
    public Action getAction(Integer forcedResponseIndex) {
        return getPrimaryAction(forcedResponseIndex);
    }

    /**
     * Parses the 0-based force-response-variant index from the {@link #FORCE_RESPONSE_INDEX_HEADER}
     * header on an incoming request. Returns {@code null} when the request is null/not an HTTP
     * request, the header is absent/blank, or the value is not an integer — in every such case normal
     * response selection applies (the header is advisory and never an error). Bounds are validated
     * later against the matched expectation's {@code httpResponses} in {@link #isForcedResponseServe}.
     */
    @JsonIgnore
    public static Integer parseForcedResponseIndex(RequestDefinition request) {
        if (!(request instanceof HttpRequest httpRequest)) {
            return null;
        }
        String value = httpRequest.getFirstHeader(FORCE_RESPONSE_INDEX_HEADER);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    /**
     * Returns the ordered list of side-effect steps (non-responder steps that appear
     * before the responder in the steps list). Returns empty list when steps is null
     * or empty, or when no non-responder steps precede the responder.
     */
    @JsonIgnore
    public List<ExpectationStep> getPreResponderSteps() {
        if (steps == null || steps.isEmpty()) {
            return Collections.emptyList();
        }
        List<ExpectationStep> pre = new ArrayList<>();
        for (ExpectationStep step : steps) {
            if (Boolean.TRUE.equals(step.getResponder())) {
                break;
            }
            pre.add(step);
        }
        return pre;
    }

    /**
     * Returns the ordered list of side-effect steps that appear after the responder
     * in the steps list (post-responder side-effects).
     */
    @JsonIgnore
    public List<ExpectationStep> getPostResponderSteps() {
        if (steps == null || steps.isEmpty()) {
            return Collections.emptyList();
        }
        List<ExpectationStep> post = new ArrayList<>();
        boolean pastResponder = false;
        for (ExpectationStep step : steps) {
            if (pastResponder) {
                post.add(step);
            }
            if (Boolean.TRUE.equals(step.getResponder())) {
                pastResponder = true;
            }
        }
        return post;
    }

    /**
     * Resolves the action target from an ExpectationStep.
     */
    @JsonIgnore
    static Action resolveStepAction(ExpectationStep step) {
        if (step.getHttpResponse() != null) {
            return step.getHttpResponse();
        }
        if (step.getHttpForward() != null) {
            return step.getHttpForward();
        }
        if (step.getHttpOverrideForwardedRequest() != null) {
            return step.getHttpOverrideForwardedRequest();
        }
        if (step.getHttpClassCallback() != null) {
            // Need to set action type depending on context; for a responder callback
            // the type is RESPONSE_CLASS_CALLBACK
            step.getHttpClassCallback().withActionType(Action.Type.RESPONSE_CLASS_CALLBACK);
            return step.getHttpClassCallback();
        }
        if (step.getHttpObjectCallback() != null) {
            step.getHttpObjectCallback().withActionType(Action.Type.RESPONSE_OBJECT_CALLBACK);
            return step.getHttpObjectCallback();
        }
        if (step.getHttpError() != null) {
            return step.getHttpError();
        }
        return null;
    }

    @JsonIgnore
    private HttpResponse selectFromResponses() {
        if (httpResponses == null || httpResponses.isEmpty()) {
            return null;
        }
        if (responseMode == ResponseMode.RANDOM) {
            return httpResponses.get(ThreadLocalRandom.current().nextInt(httpResponses.size()));
        }
        if (responseMode == ResponseMode.WEIGHTED) {
            return selectWeightedResponse();
        }
        // rotation position from the per-thread snapshot taken at match time; forced requests never
        // advance rotationCount, so they are structurally transparent to SEQUENTIAL/SWITCH order
        Integer snapshot = lastRotationSnapshot.get();
        int count = Math.max(0, (snapshot != null ? snapshot : rotationCount.get()) - 1);
        if (responseMode == ResponseMode.SWITCH) {
            return selectSwitchedResponse(count);
        }
        return httpResponses.get(count % httpResponses.size());
    }

    /**
     * Selects a response with probability proportional to its weight using cumulative-weight random
     * selection. A missing or non-positive weight is treated as 1. If the total effective weight is
     * non-positive (which cannot happen given the floor-of-1 per response, but is guarded defensively),
     * selection falls back to uniform random.
     */
    @JsonIgnore
    private HttpResponse selectWeightedResponse() {
        int size = httpResponses.size();
        int total = 0;
        int[] effectiveWeights = new int[size];
        for (int i = 0; i < size; i++) {
            Integer weight = (responseWeights != null && i < responseWeights.size()) ? responseWeights.get(i) : null;
            int effective = (weight != null && weight > 0) ? weight : 1;
            effectiveWeights[i] = effective;
            total += effective;
        }
        if (total <= 0) {
            // defensive uniform fallback
            return httpResponses.get(ThreadLocalRandom.current().nextInt(size));
        }
        int target = ThreadLocalRandom.current().nextInt(total);
        int cumulative = 0;
        for (int i = 0; i < size; i++) {
            cumulative += effectiveWeights[i];
            if (target < cumulative) {
                return httpResponses.get(i);
            }
        }
        // unreachable given target < total, but keep the compiler and edge-cases happy
        return httpResponses.get(size - 1);
    }

    /**
     * Selects a response using lightweight hit-count branching ({@link ResponseMode#SWITCH}). The first
     * {@code switchAfter} matches return the first response; each subsequent block of {@code switchAfter}
     * matches advances one index, clamping at the last response. When {@code switchAfter} is unset or
     * non-positive the threshold defaults to 1, so each match advances one index (clamped) — equivalent to
     * serving the first response once and the second response thereafter for a two-element list.
     *
     * @param zeroBasedCount the zero-based index of this match (0 for the first match)
     */
    @JsonIgnore
    private HttpResponse selectSwitchedResponse(int zeroBasedCount) {
        int threshold = (switchAfter != null && switchAfter > 0) ? switchAfter : 1;
        int index = Math.min(zeroBasedCount / threshold, httpResponses.size() - 1);
        return httpResponses.get(index);
    }

    @JsonIgnore
    public List<Action> getSecondaryActions() {
        // Only a multi-action expectation has secondary actions; the common single-action case returns
        // empty without allocating the getAllActions() list (this is called once per request).
        if (!hasMultipleConfiguredActions()) {
            return Collections.emptyList();
        }
        List<Action> all = getAllActions();
        Action primary = getPrimaryAction();
        List<Action> secondary = new ArrayList<>();
        for (Action action : all) {
            if (action != primary) {
                stampExpectationId(action);
                secondary.add(action);
            }
        }
        return secondary;
    }

    @JsonIgnore
    private List<Action> getAllActions() {
        List<Action> actions = new ArrayList<>();
        if (getHttpResponse() != null && (httpResponses == null || httpResponses.isEmpty())) {
            actions.add(getHttpResponse());
        }
        if (getHttpResponseTemplate() != null) {
            actions.add(getHttpResponseTemplate());
        }
        if (getHttpResponseClassCallback() != null) {
            actions.add(getHttpResponseClassCallback());
        }
        if (getHttpResponseObjectCallback() != null) {
            actions.add(getHttpResponseObjectCallback());
        }
        if (getHttpForward() != null) {
            actions.add(getHttpForward());
        }
        if (getHttpForwardTemplate() != null) {
            actions.add(getHttpForwardTemplate());
        }
        if (getHttpForwardClassCallback() != null) {
            actions.add(getHttpForwardClassCallback());
        }
        if (getHttpForwardObjectCallback() != null) {
            actions.add(getHttpForwardObjectCallback());
        }
        if (getHttpOverrideForwardedRequest() != null) {
            actions.add(getHttpOverrideForwardedRequest());
        }
        if (getHttpForwardValidateAction() != null) {
            actions.add(getHttpForwardValidateAction());
        }
        if (getHttpForwardWithFallback() != null) {
            actions.add(getHttpForwardWithFallback());
        }
        if (getHttpSseResponse() != null) {
            actions.add(getHttpSseResponse());
        }
        if (getHttpLlmResponse() != null) {
            actions.add(getHttpLlmResponse());
        }
        if (getHttpWebSocketResponse() != null) {
            actions.add(getHttpWebSocketResponse());
        }
        if (getGrpcStreamResponse() != null) {
            actions.add(getGrpcStreamResponse());
        }
        if (getGrpcBidiResponse() != null) {
            actions.add(getGrpcBidiResponse());
        }
        if (getBinaryResponse() != null) {
            actions.add(getBinaryResponse());
        }
        if (getDnsResponse() != null) {
            actions.add(getDnsResponse());
        }
        if (getHttpError() != null) {
            actions.add(getHttpError());
        }
        return actions;
    }

    public Times getTimes() {
        return times;
    }

    public TimeToLive getTimeToLive() {
        return timeToLive;
    }

    public Expectation thenRespond(HttpResponse httpResponse) {
        if (httpResponse != null) {
            rejectResponseMatchingBody(httpResponse);
            this.httpResponse = httpResponse;
            this.hashCode = 0;
        }
        return this;
    }

    private static void rejectResponseMatchingBody(HttpResponse httpResponse) {
        if (httpResponse != null && httpResponse.getBody() instanceof ResponseMatchingBody) {
            throw new IllegalArgumentException("a " + httpResponse.getBody().getType() + " body can only match a response in a verification, it cannot be returned as a response");
        }
    }

    public Expectation thenRespond(HttpTemplate httpTemplate) {
        if (httpTemplate != null) {
            httpTemplate.withActionType(Action.Type.RESPONSE_TEMPLATE);
            this.httpResponseTemplate = httpTemplate;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenRespond(HttpClassCallback httpClassCallback) {
        if (httpClassCallback != null) {
            httpClassCallback.withActionType(Action.Type.RESPONSE_CLASS_CALLBACK);
            this.httpResponseClassCallback = httpClassCallback;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenRespond(HttpObjectCallback httpObjectCallback) {
        if (httpObjectCallback != null) {
            httpObjectCallback.withActionType(Action.Type.RESPONSE_OBJECT_CALLBACK);
            this.httpResponseObjectCallback = httpObjectCallback;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenForward(HttpForward httpForward) {
        if (httpForward != null) {
            this.httpForward = httpForward;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenForward(HttpTemplate httpTemplate) {
        if (httpTemplate != null) {
            httpTemplate.withActionType(Action.Type.FORWARD_TEMPLATE);
            this.httpForwardTemplate = httpTemplate;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenForward(HttpClassCallback httpClassCallback) {
        if (httpClassCallback != null) {
            httpClassCallback.withActionType(Action.Type.FORWARD_CLASS_CALLBACK);
            this.httpForwardClassCallback = httpClassCallback;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenForward(HttpObjectCallback httpObjectCallback) {
        if (httpObjectCallback != null) {
            httpObjectCallback
                .withActionType(Action.Type.FORWARD_OBJECT_CALLBACK);
            this.httpForwardObjectCallback = httpObjectCallback;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenForward(HttpOverrideForwardedRequest httpOverrideForwardedRequest) {
        if (httpOverrideForwardedRequest != null) {
            this.httpOverrideForwardedRequest = httpOverrideForwardedRequest;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenForwardValidate(HttpForwardValidateAction httpForwardValidateAction) {
        if (httpForwardValidateAction != null) {
            this.httpForwardValidateAction = httpForwardValidateAction;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenForwardWithFallback(HttpForwardWithFallback httpForwardWithFallback) {
        if (httpForwardWithFallback != null) {
            this.httpForwardWithFallback = httpForwardWithFallback;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenRespondWithSse(HttpSseResponse httpSseResponse) {
        if (httpSseResponse != null) {
            this.httpSseResponse = httpSseResponse;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenRespondWithLlm(HttpLlmResponse httpLlmResponse) {
        if (httpLlmResponse != null) {
            this.httpLlmResponse = httpLlmResponse;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenRespondWithWebSocket(HttpWebSocketResponse httpWebSocketResponse) {
        if (httpWebSocketResponse != null) {
            this.httpWebSocketResponse = httpWebSocketResponse;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenRespondWithGrpcStream(GrpcStreamResponse grpcStreamResponse) {
        if (grpcStreamResponse != null) {
            this.grpcStreamResponse = grpcStreamResponse;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenRespondWithGrpcBidi(GrpcBidiResponse grpcBidiResponse) {
        if (grpcBidiResponse != null) {
            this.grpcBidiResponse = grpcBidiResponse;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenRespondWithBinary(BinaryResponse binaryResponse) {
        if (binaryResponse != null) {
            this.binaryResponse = binaryResponse;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenRespondWithDns(DnsResponse dnsResponse) {
        if (dnsResponse != null) {
            this.dnsResponse = dnsResponse;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenError(HttpError httpError) {
        if (httpError != null) {
            this.httpError = httpError;
            this.hashCode = 0;
        }
        return this;
    }

    @JsonIgnore
    public boolean isActive() {
        return hasRemainingMatches() && isStillAlive();
    }

    private boolean hasRemainingMatches() {
        return times == null || times.greaterThenZero();
    }

    private boolean isStillAlive() {
        return timeToLive == null || timeToLive.stillAlive();
    }

    public boolean decrementRemainingMatches() {
        if (times != null) {
            return times.decrement();
        }
        return false;
    }

    public boolean consumeMatch() {
        return consumeMatch(null);
    }

    /**
     * Records a match, advancing the response-sequence rotation only for a NORMAL request. A request
     * served a forced variant ({@link #isForcedResponseServe(Integer)} for {@code forcedResponseIndex}
     * is {@code true}) still decrements {@code Times} and increments {@link #matchCount}, but does NOT
     * advance {@link #rotationCount} — so it is transparent to the rotation seen by concurrent normal
     * callers (peek semantics). {@code forcedResponseIndex} is {@code null} for a normal request.
     */
    public boolean consumeMatch(Integer forcedResponseIndex) {
        if (times != null) {
            if (!times.decrementAndCheckGreaterThanZero()) {
                return false;
            }
        }
        recordMatch(forcedResponseIndex);
        return true;
    }

    public void consumeMatchLocally() {
        consumeMatchLocally(null);
    }

    /**
     * Records a match consumption WITHOUT decrementing the node-local Times
     * counter. Used when a clustered backend has already atomically
     * decremented the shared remaining-times counter via CAS — the local
     * Times object must not also decrement, or the node-local counter
     * would diverge from the shared one.
     * <p>
     * Updates matchCount, the rotation snapshot, and chaosFirstMatchEpochMillis
     * exactly as {@link #consumeMatch(Integer)} does (including the forced-request
     * rotation-transparency rule). These are node-local runtime metrics that do
     * not need to be shared across the cluster.
     */
    public void consumeMatchLocally(Integer forcedResponseIndex) {
        recordMatch(forcedResponseIndex);
    }

    /**
     * Shared match bookkeeping for {@link #consumeMatch(Integer)} and {@link #consumeMatchLocally(Integer)}.
     * Always increments {@link #matchCount} (Times/metrics/chaos-anchor semantics, unchanged). For a NORMAL
     * request it also advances {@link #rotationCount} and snapshots the new position per-thread; for a FORCED
     * request it snapshots the CURRENT (un-advanced) position so the rotation is left exactly where it was.
     */
    private void recordMatch(Integer forcedResponseIndex) {
        matchCount.incrementAndGet();
        // The rotation position is consulted only by selectFromResponses(), i.e. only for a multi-response
        // (httpResponses) expectation. For the common single-response expectation nothing reads it, so
        // neither the shared rotationCount nor the per-thread snapshot is touched — removing a contended
        // atomic write and a ThreadLocal write from every match. httpResponses only ever transitions
        // empty -> non-empty (no setter clears it), and the counter is only read once it is non-empty,
        // so skipping the advance while it is empty is unobservable.
        if (httpResponses != null && !httpResponses.isEmpty()) {
            if (isForcedResponseServe(forcedResponseIndex)) {
                lastRotationSnapshot.set(rotationCount.get());
            } else {
                lastRotationSnapshot.set(rotationCount.incrementAndGet());
            }
        }
        // record the first-match instant (via the controllable clock) once, to anchor any time-based
        // chaos outage window on this expectation. The get()==0 pre-check keeps the CAS and the
        // currentTimeMillis() call off every match after the first (get() is a cheap volatile read).
        if (chaosFirstMatchEpochMillis.get() == 0L) {
            chaosFirstMatchEpochMillis.compareAndSet(0L, TimeService.currentTimeMillis());
        }
    }

    @JsonIgnore
    public int getMatchCount() {
        return matchCount.get();
    }

    /**
     * Epoch-ms (from the controllable clock) of this expectation's first match, or
     * {@code 0} if it has not been matched yet. Anchors any time-based chaos outage
     * window. Transient runtime state — not serialized, not cloned.
     */
    @JsonIgnore
    public long getChaosFirstMatchEpochMillis() {
        return chaosFirstMatchEpochMillis.get();
    }

    @SuppressWarnings("PointlessNullCheck")
    public boolean contains(HttpRequest httpRequest) {
        return httpRequest != null && this.httpRequest.equals(httpRequest);
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public Expectation clone() {
        return cloneWith(httpRequest, httpResponse);
    }

    /**
     * As {@link #clone()}, with the request matcher and the response replaced (a {@code null} response leaves none):
     * for a redacted copy that keeps every other part of the expectation.
     */
    public Expectation cloneWith(RequestDefinition httpRequest, HttpResponse httpResponse) {
        Expectation clone = new Expectation(httpRequest, times.clone(), timeToLive, priority)
            .withId(id)
            .withCreated(created)
            .withPercentage(percentage)
            .withChaos(chaos)
            .withRateLimit(rateLimit)
            .withNamespace(namespace)
            .withScenarioName(scenarioName)
            .withScenarioState(scenarioState)
            .withNewScenarioState(newScenarioState)
            .thenRespond(httpResponse)
            .thenForward(httpForward)
            .thenForward(httpOverrideForwardedRequest)
            .thenForwardValidate(httpForwardValidateAction)
            .thenForwardWithFallback(httpForwardWithFallback)
            .thenRespondWithSse(httpSseResponse)
            .thenRespondWithLlm(httpLlmResponse)
            .thenRespondWithWebSocket(httpWebSocketResponse)
            .thenRespondWithGrpcStream(grpcStreamResponse)
            .thenRespondWithGrpcBidi(grpcBidiResponse)
            .thenRespondWithBinary(binaryResponse)
            .thenRespondWithDns(dnsResponse)
            .thenError(httpError)
            .thenRespond(httpResponses)
            .withResponseMode(responseMode)
            .withResponseWeights(responseWeights)
            .withSwitchAfter(switchAfter);
        // assigned rather than set through thenRespond / thenForward, which stamp the action type on the (shared) action:
        // these were stamped when they were set on this expectation
        clone.httpResponseTemplate = httpResponseTemplate;
        clone.httpResponseClassCallback = httpResponseClassCallback;
        clone.httpResponseObjectCallback = httpResponseObjectCallback;
        clone.httpForwardTemplate = httpForwardTemplate;
        clone.httpForwardClassCallback = httpForwardClassCallback;
        clone.httpForwardObjectCallback = httpForwardObjectCallback;
        if (beforeActions != null) {
            clone.beforeActions = new ArrayList<>(beforeActions);
        }
        if (afterActions != null) {
            clone.afterActions = new ArrayList<>(afterActions);
        }
        if (steps != null) {
            clone.steps = new ArrayList<>(steps);
        }
        if (crossProtocolScenarios != null) {
            clone.crossProtocolScenarios = new ArrayList<>(crossProtocolScenarios);
        }
        if (capture != null) {
            clone.capture = new ArrayList<>(capture);
        }
        // shared rather than copied: the map is unmodifiable, so the clone costs no allocation
        clone.metadata = metadata;
        clone.metadataJson = metadataJson;
        return clone;
    }

    @Override
    @JsonIgnore
    public String[] fieldsExcludedFromEqualsAndHashCode() {
        return excludedFields;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        Expectation that = (Expectation) o;
        return Objects.equals(priority, that.priority) &&
            Objects.equals(percentage, that.percentage) &&
            Objects.equals(chaos, that.chaos) &&
            Objects.equals(httpRequest, that.httpRequest) &&
            Objects.equals(times, that.times) &&
            Objects.equals(timeToLive, that.timeToLive) &&
            Objects.equals(httpResponse, that.httpResponse) &&
            Objects.equals(httpResponseTemplate, that.httpResponseTemplate) &&
            Objects.equals(httpResponseClassCallback, that.httpResponseClassCallback) &&
            Objects.equals(httpResponseObjectCallback, that.httpResponseObjectCallback) &&
            Objects.equals(httpForward, that.httpForward) &&
            Objects.equals(httpForwardTemplate, that.httpForwardTemplate) &&
            Objects.equals(httpForwardClassCallback, that.httpForwardClassCallback) &&
            Objects.equals(httpForwardObjectCallback, that.httpForwardObjectCallback) &&
            Objects.equals(httpOverrideForwardedRequest, that.httpOverrideForwardedRequest) &&
            Objects.equals(httpForwardValidateAction, that.httpForwardValidateAction) &&
            Objects.equals(httpForwardWithFallback, that.httpForwardWithFallback) &&
            Objects.equals(httpSseResponse, that.httpSseResponse) &&
            Objects.equals(httpLlmResponse, that.httpLlmResponse) &&
            Objects.equals(httpWebSocketResponse, that.httpWebSocketResponse) &&
            Objects.equals(grpcStreamResponse, that.grpcStreamResponse) &&
            Objects.equals(grpcBidiResponse, that.grpcBidiResponse) &&
            Objects.equals(binaryResponse, that.binaryResponse) &&
            Objects.equals(dnsResponse, that.dnsResponse) &&
            Objects.equals(httpError, that.httpError) &&
            Objects.equals(beforeActions, that.beforeActions) &&
            Objects.equals(afterActions, that.afterActions) &&
            Objects.equals(steps, that.steps) &&
            Objects.equals(httpResponses, that.httpResponses) &&
            Objects.equals(responseMode, that.responseMode) &&
            Objects.equals(responseWeights, that.responseWeights) &&
            Objects.equals(switchAfter, that.switchAfter) &&
            Objects.equals(namespace, that.namespace) &&
            Objects.equals(metadata, that.metadata) &&
            Objects.equals(scenarioName, that.scenarioName) &&
            Objects.equals(scenarioState, that.scenarioState) &&
            Objects.equals(newScenarioState, that.newScenarioState) &&
            Objects.equals(crossProtocolScenarios, that.crossProtocolScenarios) &&
            Objects.equals(capture, that.capture);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(priority, percentage, chaos, httpRequest, times, timeToLive, httpResponse, httpResponseTemplate, httpResponseClassCallback, httpResponseObjectCallback, httpForward, httpForwardTemplate, httpForwardClassCallback, httpForwardObjectCallback, httpOverrideForwardedRequest, httpForwardValidateAction, httpForwardWithFallback, httpSseResponse, httpLlmResponse, httpWebSocketResponse, grpcStreamResponse, grpcBidiResponse, binaryResponse, dnsResponse, httpError, beforeActions, afterActions, steps, httpResponses, responseMode, responseWeights, switchAfter, namespace, metadata, scenarioName, scenarioState, newScenarioState, crossProtocolScenarios, capture);
        }
        return hashCode;
    }
}
