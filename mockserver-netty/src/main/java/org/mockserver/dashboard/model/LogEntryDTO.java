package org.mockserver.dashboard.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.log.model.LogEntry;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.ObjectWithJsonToString;
import org.slf4j.event.Level;

import static org.mockserver.model.HttpRequest.request;

public class LogEntryDTO extends ObjectWithJsonToString {

    private static final String[] EXCLUDED_FIELDS = {
        "key",
        "timestamp",
        "message",
        "throwable"
    };
    private String key;
    private Level logLevel;
    private long epochTime;
    private String timestamp;
    private LogEntry.LogMessageType type;
    private HttpRequest[] httpRequests;
    private HttpResponse httpResponse;
    private HttpError httpError;
    private Expectation expectation;
    private Throwable throwable;

    private String messageFormat;
    private Object[] arguments;
    private String message;

    public LogEntryDTO(LogEntry logEntry) {
        setKey(logEntry.key());
        setLogLevel(logEntry.getLogLevel());
        setTimestamp(logEntry.getTimestamp());
        setEpochTime(logEntry.getEpochTime());
        setType(logEntry.getType());
        setHttpRequests(logEntry.getHttpRequests());
        setHttpResponse(logEntry.getHttpResponse());
        setHttpError(logEntry.getHttpError());
        setExpectation(logEntry.getExpectation());
        setMessageFormat(logEntry.getMessageFormat());
        setArguments(logEntry.getArguments());
        setMessage(logEntry.getMessage());
        setThrowable(logEntry.getThrowable());
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Level getLogLevel() {
        return logLevel;
    }

    public LogEntryDTO setLogLevel(Level logLevel) {
        this.logLevel = logLevel;
        return this;
    }

    public long getEpochTime() {
        return epochTime;
    }

    public LogEntryDTO setEpochTime(long epochTime) {
        this.epochTime = epochTime;
        return this;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public LogEntry.LogMessageType getType() {
        return type;
    }

    public LogEntryDTO setType(LogEntry.LogMessageType type) {
        this.type = type;
        return this;
    }

    @JsonIgnore
    public HttpRequest[] getHttpRequests() {
        return httpRequests;
    }

    public LogEntryDTO setHttpRequests(HttpRequest[] httpRequests) {
        this.httpRequests = httpRequests;
        return this;
    }

    public LogEntryDTO setHttpRequest(HttpRequest httpRequest) {
        if (httpRequest != null) {
            this.httpRequests = new HttpRequest[]{httpRequest};
        } else {
            this.httpRequests = new HttpRequest[]{request()};
        }
        return this;
    }

    public HttpRequest getHttpRequest() {
        if (httpRequests != null && httpRequests.length > 0) {
            return httpRequests[0];
        } else {
            return null;
        }
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    public LogEntryDTO setHttpResponse(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
        return this;
    }

    public HttpError getHttpError() {
        return httpError;
    }

    public LogEntryDTO setHttpError(HttpError httpError) {
        this.httpError = httpError;
        return this;
    }

    public Expectation getExpectation() {
        return expectation;
    }

    public LogEntryDTO setExpectation(Expectation expectation) {
        this.expectation = expectation;
        return this;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public LogEntryDTO setThrowable(Throwable throwable) {
        this.throwable = throwable;
        return this;
    }

    public String getMessageFormat() {
        return messageFormat;
    }

    public LogEntryDTO setMessageFormat(String messageFormat) {
        this.messageFormat = messageFormat;
        return this;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public LogEntryDTO setArguments(Object... arguments) {
        this.arguments = arguments;
        return this;
    }

    @JsonIgnore
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
