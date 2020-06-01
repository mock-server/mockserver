package org.mockserver.mock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.*;
import org.mockserver.uuid.UUIDService;

import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public class Expectation extends ObjectWithJsonToString {

    private static final String[] excludedFields = {"id", "created"};
    private static final AtomicInteger EXPECTATION_COUNTER = new AtomicInteger(0);
    private static final long START_TIME = System.currentTimeMillis();
    private String id;
    @JsonIgnore
    private long created;
    private int hashCode;
    private final Integer priority;
    private final HttpRequest httpRequest;
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

    public static final Comparator<Expectation> EXPECTATION_PRIORITY_COMPARATOR = Comparator
        .comparing(Expectation::getPriority, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(Expectation::getCreated, Comparator.nullsLast(Comparator.naturalOrder()));

    public static Expectation when(HttpRequest httpRequest) {
        return new Expectation(httpRequest);
    }

    public static Expectation when(HttpRequest httpRequest, Integer priority) {
        return new Expectation(httpRequest, Times.unlimited(), TimeToLive.unlimited(), priority);
    }

    public static Expectation when(HttpRequest httpRequest, Times times, TimeToLive timeToLive) {
        return new Expectation(httpRequest, times, timeToLive, 0);
    }

    public static Expectation when(HttpRequest httpRequest, Times times, TimeToLive timeToLive, Integer priority) {
        return new Expectation(httpRequest, times, timeToLive, priority);
    }

    public Expectation(HttpRequest httpRequest) {
        this(httpRequest, Times.unlimited(), TimeToLive.unlimited(), 0);
    }

    public Expectation(HttpRequest httpRequest, Times times, TimeToLive timeToLive, Integer priority) {
        // ensure created enforces insertion order by relying on system time, and a counter
        EXPECTATION_COUNTER.compareAndSet(Integer.MAX_VALUE, 0);
        this.created = System.currentTimeMillis() - START_TIME + EXPECTATION_COUNTER.incrementAndGet();
        this.httpRequest = httpRequest;
        this.times = times;
        this.timeToLive = timeToLive;
        this.priority = priority;
    }

    public Expectation withId(String key) {
        this.id = key;
        return this;
    }

    public String getId() {
        if (id == null) {
            withId(UUIDService.getUUID());
        }
        return id;
    }

    public Integer getPriority() {
        return priority;
    }

    public Expectation withCreated(long created) {
        this.created = created;
        return this;
    }

    public long getCreated() {
        return created;
    }

    public HttpRequest getHttpRequest() {
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
        if (httpResponse != null) {
            return getHttpResponse();
        } else if (httpResponseTemplate != null) {
            return getHttpResponseTemplate();
        } else if (httpResponseClassCallback != null) {
            return getHttpResponseClassCallback();
        } else if (httpResponseObjectCallback != null) {
            return getHttpResponseObjectCallback();
        } else if (httpForward != null) {
            return getHttpForward();
        } else if (httpForwardTemplate != null) {
            return getHttpForwardTemplate();
        } else if (httpForwardClassCallback != null) {
            return getHttpForwardClassCallback();
        } else if (httpForwardObjectCallback != null) {
            return getHttpForwardObjectCallback();
        } else if (httpOverrideForwardedRequest != null) {
            return getHttpOverrideForwardedRequest();
        } else if (httpError != null) {
            return getHttpError();
        } else {
            return null;
        }
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
