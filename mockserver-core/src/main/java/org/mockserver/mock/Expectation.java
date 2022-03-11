package org.mockserver.mock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;
import org.mockserver.uuid.UUIDService;

import java.util.Objects;
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
    private HttpError httpError;

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

    public String getId() {
        if (id == null) {
            withId(UUIDService.getUUID());
        }
        return id;
    }

    /**
     * <p>
     * Set priority of this expectation which is used to determin the matching
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

    public HttpError getHttpError() {
        return httpError;
    }

    @JsonIgnore
    public Action getAction() {
        Action action = null;
        if (httpResponse != null) {
            action = getHttpResponse();
        } else if (httpResponseTemplate != null) {
            action = getHttpResponseTemplate();
        } else if (httpResponseClassCallback != null) {
            action = getHttpResponseClassCallback();
        } else if (httpResponseObjectCallback != null) {
            action = getHttpResponseObjectCallback();
        } else if (httpForward != null) {
            action = getHttpForward();
        } else if (httpForwardTemplate != null) {
            action = getHttpForwardTemplate();
        } else if (httpForwardClassCallback != null) {
            action = getHttpForwardClassCallback();
        } else if (httpForwardObjectCallback != null) {
            action = getHttpForwardObjectCallback();
        } else if (httpOverrideForwardedRequest != null) {
            action = getHttpOverrideForwardedRequest();
        } else if (httpError != null) {
            action = getHttpError();
        }
        if (action != null) {
            action.setExpectationId(getId());
        }
        return action;
    }

    public Times getTimes() {
        return times;
    }

    public TimeToLive getTimeToLive() {
        return timeToLive;
    }

    public Expectation thenRespond(HttpResponse httpResponse) {
        if (httpResponse != null) {
            validationErrors("a response", httpResponse.getType());
            this.httpResponse = httpResponse;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenRespond(HttpTemplate httpTemplate) {
        if (httpTemplate != null) {
            httpTemplate.withActionType(Action.Type.RESPONSE_TEMPLATE);
            validationErrors("a response template", httpTemplate.getType());
            this.httpResponseTemplate = httpTemplate;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenRespond(HttpClassCallback httpClassCallback) {
        if (httpClassCallback != null) {
            httpClassCallback.withActionType(Action.Type.RESPONSE_CLASS_CALLBACK);
            validationErrors("a response class callback", httpClassCallback.getType());
            this.httpResponseClassCallback = httpClassCallback;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenRespond(HttpObjectCallback httpObjectCallback) {
        if (httpObjectCallback != null) {
            httpObjectCallback.withActionType(Action.Type.RESPONSE_OBJECT_CALLBACK);
            validationErrors("a response object callback", httpObjectCallback.getType());
            this.httpResponseObjectCallback = httpObjectCallback;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenForward(HttpForward httpForward) {
        if (httpForward != null) {
            validationErrors("a forward", httpForward.getType());
            this.httpForward = httpForward;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenForward(HttpTemplate httpTemplate) {
        if (httpTemplate != null) {
            httpTemplate.withActionType(Action.Type.FORWARD_TEMPLATE);
            validationErrors("a forward template", httpTemplate.getType());
            this.httpForwardTemplate = httpTemplate;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenForward(HttpClassCallback httpClassCallback) {
        if (httpClassCallback != null) {
            httpClassCallback.withActionType(Action.Type.FORWARD_CLASS_CALLBACK);
            validationErrors("a forward class callback", httpClassCallback.getType());
            this.httpForwardClassCallback = httpClassCallback;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenForward(HttpObjectCallback httpObjectCallback) {
        if (httpObjectCallback != null) {
            httpObjectCallback
                .withActionType(Action.Type.FORWARD_OBJECT_CALLBACK);
            validationErrors("a forward object callback", httpObjectCallback.getType());
            this.httpForwardObjectCallback = httpObjectCallback;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenForward(HttpOverrideForwardedRequest httpOverrideForwardedRequest) {
        if (httpOverrideForwardedRequest != null) {
            validationErrors("a forward replace", httpOverrideForwardedRequest.getType());
            this.httpOverrideForwardedRequest = httpOverrideForwardedRequest;
            this.hashCode = 0;
        }
        return this;
    }

    public Expectation thenError(HttpError httpError) {
        if (httpError != null) {
            validationErrors("an error", httpError.getType());
            this.httpError = httpError;
            this.hashCode = 0;
        }
        return this;
    }

    private void validationErrors(String actionDescription, Action.Type actionType) {
        if (actionType != Action.Type.RESPONSE && httpResponse != null) {
            throw new IllegalArgumentException("It is not possible to set " + actionDescription + " once a response has been set");
        }
        if (actionType != Action.Type.RESPONSE_TEMPLATE && httpResponseTemplate != null) {
            throw new IllegalArgumentException("It is not possible to set " + actionDescription + " once a response template has been set");
        }
        if (actionType != Action.Type.RESPONSE_CLASS_CALLBACK && httpResponseClassCallback != null) {
            throw new IllegalArgumentException("It is not possible to set " + actionDescription + " once a class callback has been set");
        }
        if (actionType != Action.Type.RESPONSE_OBJECT_CALLBACK && httpResponseObjectCallback != null) {
            throw new IllegalArgumentException("It is not possible to set " + actionDescription + " once an object callback has been set");
        }
        if (actionType != Action.Type.FORWARD && httpForward != null) {
            throw new IllegalArgumentException("It is not possible to set " + actionDescription + " once a forward has been set");
        }
        if (actionType != Action.Type.FORWARD_TEMPLATE && httpForwardTemplate != null) {
            throw new IllegalArgumentException("It is not possible to set " + actionDescription + " once a forward template has been set");
        }
        if (actionType != Action.Type.FORWARD_CLASS_CALLBACK && httpForwardClassCallback != null) {
            throw new IllegalArgumentException("It is not possible to set " + actionDescription + " once a class callback has been set");
        }
        if (actionType != Action.Type.FORWARD_OBJECT_CALLBACK && httpForwardObjectCallback != null) {
            throw new IllegalArgumentException("It is not possible to set " + actionDescription + " once an object callback has been set");
        }
        if (actionType != Action.Type.FORWARD_REPLACE && httpOverrideForwardedRequest != null) {
            throw new IllegalArgumentException("It is not possible to set " + actionDescription + " once a forward replace has been set");
        }
        if (actionType != Action.Type.ERROR && httpError != null) {
            throw new IllegalArgumentException("It is not possible to set " + actionDescription + " callback once an error has been set");
        }
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

    @SuppressWarnings("PointlessNullCheck")
    public boolean contains(HttpRequest httpRequest) {
        return httpRequest != null && this.httpRequest.equals(httpRequest);
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public Expectation clone() {
        return new Expectation(httpRequest, times.clone(), timeToLive, priority)
            .withId(id)
            .withCreated(created)
            .thenRespond(httpResponse)
            .thenRespond(httpResponseTemplate)
            .thenRespond(httpResponseClassCallback)
            .thenRespond(httpResponseObjectCallback)
            .thenForward(httpForward)
            .thenForward(httpForwardTemplate)
            .thenForward(httpForwardClassCallback)
            .thenForward(httpForwardObjectCallback)
            .thenForward(httpOverrideForwardedRequest)
            .thenError(httpError);
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
            Objects.equals(httpError, that.httpError);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(priority, httpRequest, times, timeToLive, httpResponse, httpResponseTemplate, httpResponseClassCallback, httpResponseObjectCallback, httpForward, httpForwardTemplate, httpForwardClassCallback, httpForwardObjectCallback, httpOverrideForwardedRequest, httpError);
        }
        return hashCode;
    }
}
