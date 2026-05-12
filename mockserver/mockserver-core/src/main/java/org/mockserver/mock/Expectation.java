package org.mockserver.mock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;
import org.mockserver.uuid.UUIDService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockserver.model.OpenAPIDefinition.openAPI;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public class Expectation extends ObjectWithJsonToString {

    private static final String[] excludedFields = {"id", "created", "sortableExpectationId"};
    private static final AtomicInteger EXPECTATION_COUNTER = new AtomicInteger(0);
    private static final long START_TIME = System.currentTimeMillis();
    private int hashCode;
    private String id;
    @JsonIgnore
    private long created;
    private int priority;
    private Integer percentage;
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
    private HttpSseResponse httpSseResponse;
    private HttpWebSocketResponse httpWebSocketResponse;
    private GrpcStreamResponse grpcStreamResponse;
    private BinaryResponse binaryResponse;
    private DnsResponse dnsResponse;
    private HttpError httpError;
    private List<AfterAction> afterActions;
    private List<HttpResponse> httpResponses;
    private ResponseMode responseMode;
    private String scenarioName;
    private String scenarioState;
    private String newScenarioState;
    @JsonIgnore
    private final AtomicInteger matchCount = new AtomicInteger(0);
    @JsonIgnore
    private final ThreadLocal<Integer> lastConsumedCount = new ThreadLocal<>();

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

    public HttpSseResponse getHttpSseResponse() {
        return httpSseResponse;
    }

    public HttpWebSocketResponse getHttpWebSocketResponse() {
        return httpWebSocketResponse;
    }

    public GrpcStreamResponse getGrpcStreamResponse() {
        return grpcStreamResponse;
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

    public List<AfterAction> getAfterActions() {
        return afterActions != null ? Collections.unmodifiableList(afterActions) : null;
    }

    public List<HttpResponse> getHttpResponses() {
        return httpResponses != null ? Collections.unmodifiableList(httpResponses) : null;
    }

    public Expectation thenRespond(List<HttpResponse> httpResponses) {
        if (httpResponses != null && !httpResponses.isEmpty()) {
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

    public Expectation withAfterActions(AfterAction... afterActions) {
        if (afterActions != null && afterActions.length > 0) {
            this.afterActions = new ArrayList<>(Arrays.asList(afterActions));
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation withAfterActions(List<AfterAction> afterActions) {
        if (afterActions != null && !afterActions.isEmpty()) {
            this.afterActions = new ArrayList<>(afterActions);
            this.hashCode = 0;
        }
        return this;
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

    @JsonIgnore
    public Action getAction() {
        return getPrimaryAction();
    }

    @JsonIgnore
    public Action getPrimaryAction() {
        if (httpResponses != null && !httpResponses.isEmpty()) {
            HttpResponse selected = selectFromResponses();
            if (selected != null) {
                selected.setExpectationId(getId());
                return selected;
            }
        }
        List<Action> actions = getAllActions();
        if (actions.isEmpty()) {
            return null;
        }
        if (actions.size() == 1) {
            Action action = actions.get(0);
            action.setExpectationId(getId());
            return action;
        }
        Action primary = null;
        for (Action action : actions) {
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
        primary.setExpectationId(getId());
        return primary;
    }

    @JsonIgnore
    private HttpResponse selectFromResponses() {
        if (httpResponses == null || httpResponses.isEmpty()) {
            return null;
        }
        if (responseMode == ResponseMode.RANDOM) {
            return httpResponses.get(ThreadLocalRandom.current().nextInt(httpResponses.size()));
        }
        Integer consumed = lastConsumedCount.get();
        int count = Math.max(0, (consumed != null ? consumed : matchCount.get()) - 1);
        return httpResponses.get(count % httpResponses.size());
    }

    @JsonIgnore
    public List<Action> getSecondaryActions() {
        List<Action> all = getAllActions();
        if (all.size() <= 1) {
            return Collections.emptyList();
        }
        Action primary = getPrimaryAction();
        List<Action> secondary = new ArrayList<>();
        for (Action action : all) {
            if (action != primary) {
                action.setExpectationId(getId());
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
        if (getHttpSseResponse() != null) {
            actions.add(getHttpSseResponse());
        }
        if (getHttpWebSocketResponse() != null) {
            actions.add(getHttpWebSocketResponse());
        }
        if (getGrpcStreamResponse() != null) {
            actions.add(getGrpcStreamResponse());
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
            this.httpResponse = httpResponse;
            this.hashCode = 0;
        }
        return this;
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

    public Expectation thenRespondWithSse(HttpSseResponse httpSseResponse) {
        if (httpSseResponse != null) {
            this.httpSseResponse = httpSseResponse;
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
        if (times != null) {
            if (!times.decrementAndCheckGreaterThanZero()) {
                return false;
            }
        }
        lastConsumedCount.set(matchCount.incrementAndGet());
        return true;
    }

    @JsonIgnore
    public int getMatchCount() {
        return matchCount.get();
    }

    @SuppressWarnings("PointlessNullCheck")
    public boolean contains(HttpRequest httpRequest) {
        return httpRequest != null && this.httpRequest.equals(httpRequest);
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public Expectation clone() {
        Expectation clone = new Expectation(httpRequest, times.clone(), timeToLive, priority)
            .withId(id)
            .withCreated(created)
            .withPercentage(percentage)
            .withScenarioName(scenarioName)
            .withScenarioState(scenarioState)
            .withNewScenarioState(newScenarioState)
            .thenRespond(httpResponse)
            .thenRespond(httpResponseTemplate)
            .thenRespond(httpResponseClassCallback)
            .thenRespond(httpResponseObjectCallback)
            .thenForward(httpForward)
            .thenForward(httpForwardTemplate)
            .thenForward(httpForwardClassCallback)
            .thenForward(httpForwardObjectCallback)
            .thenForward(httpOverrideForwardedRequest)
            .thenForwardValidate(httpForwardValidateAction)
            .thenRespondWithSse(httpSseResponse)
            .thenRespondWithWebSocket(httpWebSocketResponse)
            .thenRespondWithGrpcStream(grpcStreamResponse)
            .thenRespondWithBinary(binaryResponse)
            .thenRespondWithDns(dnsResponse)
            .thenError(httpError)
            .thenRespond(httpResponses)
            .withResponseMode(responseMode);
        if (afterActions != null) {
            clone.afterActions = new ArrayList<>(afterActions);
        }
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
            Objects.equals(httpSseResponse, that.httpSseResponse) &&
            Objects.equals(httpWebSocketResponse, that.httpWebSocketResponse) &&
            Objects.equals(grpcStreamResponse, that.grpcStreamResponse) &&
            Objects.equals(binaryResponse, that.binaryResponse) &&
            Objects.equals(dnsResponse, that.dnsResponse) &&
            Objects.equals(httpError, that.httpError) &&
            Objects.equals(afterActions, that.afterActions) &&
            Objects.equals(httpResponses, that.httpResponses) &&
            Objects.equals(responseMode, that.responseMode) &&
            Objects.equals(scenarioName, that.scenarioName) &&
            Objects.equals(scenarioState, that.scenarioState) &&
            Objects.equals(newScenarioState, that.newScenarioState);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(priority, percentage, httpRequest, times, timeToLive, httpResponse, httpResponseTemplate, httpResponseClassCallback, httpResponseObjectCallback, httpForward, httpForwardTemplate, httpForwardClassCallback, httpForwardObjectCallback, httpOverrideForwardedRequest, httpForwardValidateAction, httpSseResponse, httpWebSocketResponse, grpcStreamResponse, binaryResponse, dnsResponse, httpError, afterActions, httpResponses, responseMode, scenarioName, scenarioState, newScenarioState);
        }
        return hashCode;
    }
}
