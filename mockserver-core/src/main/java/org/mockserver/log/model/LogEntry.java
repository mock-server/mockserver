package org.mockserver.log.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmax.disruptor.EventTranslator;
import org.mockserver.log.TimeService;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.uuid.UUIDService;
import org.slf4j.event.Level;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class LogEntry extends ObjectWithJsonToString implements EventTranslator<LogEntry> {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();
    private static final String[] EXCLUDED_FIELDS = {
        "id",
        "timestamp",
        "message",
        "throwable"
    };
    private String id;
    private Level logLevel = Level.INFO;
    public static final DateFormat LOG_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private long epochTime = TimeService.currentTimeMillis();
    private String timestamp;
    private LogEntry.LogMessageType type;
    private HttpRequest[] httpRequests;
    private HttpRequest[] httpUpdatedRequests;
    private HttpResponse httpResponse;
    private HttpResponse httpUpdatedResponse;
    private HttpError httpError;
    private Expectation expectation;
    private Throwable throwable;
    private Runnable consumer;

    private String messageFormat;
    private Object[] arguments;
    private String message;

    public LogEntry() {

    }

    @JsonIgnore
    public String id() {
        if (id == null) {
            id = UUIDService.getUUID();
        }
        return id;
    }

    public void clear() {
        logLevel = Level.INFO;
        epochTime = -1;
        httpRequests = new HttpRequest[]{request()};
        timestamp = null;
        httpResponse = null;
        httpError = null;
        expectation = null;
        throwable = null;
        consumer = null;
        messageFormat = null;
        arguments = null;
        message = null;
    }

    public Level getLogLevel() {
        return logLevel;
    }

    public LogEntry setLogLevel(Level logLevel) {
        this.logLevel = logLevel;
        if (type == null) {
            type = LogMessageType.valueOf(logLevel.name());
        }
        return this;
    }

    public long getEpochTime() {
        return epochTime;
    }

    public LogEntry setEpochTime(long epochTime) {
        this.epochTime = epochTime;
        this.timestamp = null;
        return this;
    }

    public String getTimestamp() {
        if (timestamp == null) {
            timestamp = LOG_DATE_FORMAT.format(new Date(epochTime));
        }
        return timestamp;
    }

    public LogEntry.LogMessageType getType() {
        return type;
    }

    public LogEntry setType(LogEntry.LogMessageType type) {
        this.type = type;
        return this;
    }

    @JsonIgnore
    public HttpRequest[] getHttpRequests() {
        if (httpRequests == null) {
            return new HttpRequest[0];
        } else {
            return httpRequests;
        }
    }

    @JsonIgnore
    public HttpRequest[] getHttpUpdatedRequests() {
        if (httpRequests == null) {
            return new HttpRequest[0];
        } else if (httpUpdatedRequests == null) {
            httpUpdatedRequests = Arrays
                .stream(httpRequests)
                .map(this::updateBody)
                .toArray(HttpRequest[]::new);
            return httpUpdatedRequests;
        } else {
            return httpUpdatedRequests;
        }
    }

    @JsonIgnore
    public boolean matches(HttpRequestMatcher matcher) {
        if (matcher == null) {
            return true;
        }
        if (httpRequests == null || httpRequests.length == 0) {
            return true;
        }
        for (HttpRequest httpRequest : httpRequests) {
            if (matcher.matches(httpRequest)) {
                return true;
            }
        }
        return false;
    }

    public LogEntry setHttpRequests(HttpRequest[] httpRequests) {
        this.httpRequests = httpRequests;
        return this;
    }

    public HttpRequest getHttpRequest() {
        if (httpRequests != null && httpRequests.length > 0) {
            return httpRequests[0];
        } else {
            return null;
        }
    }

    public LogEntry setHttpRequest(HttpRequest httpRequest) {
        if (httpRequest != null) {
            this.httpRequests = new HttpRequest[]{httpRequest};
        } else {
            this.httpRequests = new HttpRequest[]{request()};
        }
        return this;
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    public HttpResponse getHttpUpdatedResponse() {
        if (httpResponse == null) {
            return null;
        } else if (httpUpdatedResponse == null) {
            httpUpdatedResponse = updateBody(httpResponse);
            return httpUpdatedResponse;
        } else {
            return httpUpdatedResponse;
        }
    }

    public LogEntry setHttpResponse(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
        return this;
    }

    public HttpError getHttpError() {
        return httpError;
    }

    public LogEntry setHttpError(HttpError httpError) {
        this.httpError = httpError;
        return this;
    }

    public Expectation getExpectation() {
        return expectation;
    }

    public LogEntry setExpectation(Expectation expectation) {
        this.expectation = expectation;
        return this;
    }

    public LogEntry setExpectation(HttpRequest httpRequest, HttpResponse httpResponse) {
        this.expectation = new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponse);
        return this;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public LogEntry setThrowable(Throwable throwable) {
        this.throwable = throwable;
        return this;
    }

    public Runnable getConsumer() {
        return consumer;
    }

    public LogEntry setConsumer(Runnable consumer) {
        this.consumer = consumer;
        return this;
    }

    public String getMessageFormat() {
        return messageFormat;
    }

    public LogEntry setMessageFormat(String messageFormat) {
        this.messageFormat = messageFormat;
        return this;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public LogEntry setArguments(Object... arguments) {
        if (arguments != null) {
            this.arguments = Arrays
                .stream(arguments)
                .map(argument -> {
                    if (argument instanceof HttpRequest) {
                        return updateBody((HttpRequest) argument);
                    } else if (argument instanceof HttpResponse) {
                        return updateBody((HttpResponse) argument);
                    } else {
                        return argument;
                    }
                })
                .toArray(Object[]::new);
        } else {
            this.arguments = null;
        }
        return this;
    }

    @JsonIgnore
    public String getMessage() {
        if (message == null) {
            if (arguments != null) {
                message = formatLogMessage(messageFormat, arguments);
            } else {
                message = messageFormat;
            }
        }
        return message;
    }

    private HttpRequest updateBody(HttpRequest httpRequest) {
        if (httpRequest != null) {
            Body body = httpRequest.getBody();
            if (body != null && JsonBody.class.isAssignableFrom(body.getClass())) {
                try {
                    return httpRequest
                        .clone()
                        .withBody(
                            new LogEventBody(OBJECT_MAPPER.readTree(body.toString()))
                        );
                } catch (Throwable throwable) {
                    return httpRequest
                        .clone()
                        .withBody(
                            new LogEventBody(body.toString())
                        );
                }
            } else if (body != null && !(body instanceof LogEventBody) && BodyWithContentType.class.isAssignableFrom(body.getClass())) {
                return httpRequest
                    .clone()
                    .withBody(
                        new LogEventBody(body.toString())
                    );
            } else {
                return httpRequest;
            }
        } else {
            return null;
        }
    }

    private HttpResponse updateBody(HttpResponse httpResponse) {
        if (httpResponse != null) {
            Body body = httpResponse.getBody();
            if (body != null && JsonBody.class.isAssignableFrom(body.getClass())) {
                try {
                    return httpResponse
                        .clone()
                        .withBody(
                            new LogEventBody(OBJECT_MAPPER.readTree(body.toString()))
                        );
                } catch (Throwable throwable) {
                    return httpResponse
                        .clone()
                        .withBody(
                            new LogEventBody(body.toString())
                        );
                }
            } else if (body != null && !(body instanceof LogEventBody)) {
                return httpResponse
                    .clone()
                    .withBody(
                        new LogEventBody(body.toString())
                    );
            } else {
                return httpResponse;
            }
        } else {
            return null;
        }
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public LogEntry clone() {
        return new LogEntry()
            .setType(getType())
            .setLogLevel(getLogLevel())
            .setEpochTime(getEpochTime())
            .setHttpRequests(getHttpRequests())
            .setHttpResponse(getHttpResponse())
            .setHttpError(getHttpError())
            .setExpectation(getExpectation())
            .setMessageFormat(getMessageFormat())
            .setArguments(getArguments())
            .setThrowable(getThrowable())
            .setConsumer(getConsumer());
    }

    @Override
    public void translateTo(LogEntry event, long sequence) {
        event
            .setType(getType())
            .setLogLevel(getLogLevel())
            .setEpochTime(getEpochTime())
            .setHttpRequests(getHttpRequests())
            .setHttpResponse(getHttpResponse())
            .setHttpError(getHttpError())
            .setExpectation(getExpectation())
            .setMessageFormat(getMessageFormat())
            .setArguments(getArguments())
            .setThrowable(getThrowable())
            .setConsumer(getConsumer());
        clear();
    }

    public enum LogMessageType {
        RUNNABLE,
        TRACE,
        DEBUG,
        INFO,
        WARN,
        EXCEPTION,
        CLEARED,
        RETRIEVED,
        UPDATED_EXPECTATION,
        CREATED_EXPECTATION,
        REMOVED_EXPECTATION,
        RECEIVED_REQUEST,
        EXPECTATION_RESPONSE,
        EXPECTATION_NOT_MATCHED_RESPONSE,
        EXPECTATION_MATCHED,
        EXPECTATION_NOT_MATCHED,
        VERIFICATION,
        VERIFICATION_FAILED,
        FORWARDED_REQUEST,
        TEMPLATE_GENERATED,
        SERVER_CONFIGURATION,
    }

    @Override
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
