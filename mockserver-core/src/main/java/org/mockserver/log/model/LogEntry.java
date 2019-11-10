package org.mockserver.log.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lmax.disruptor.EventTranslator;
import org.mockserver.log.TimeService;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.ObjectWithJsonToString;
import org.slf4j.event.Level;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class LogEntry extends ObjectWithJsonToString implements EventTranslator<LogEntry> {

    private static final String[] EXCLUDED_FIELDS = {
        "key",
        "timestamp",
        "message",
        "throwable"
    };
    private Level logLevel = Level.INFO;
    public static final DateFormat LOG_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private long epochTime = TimeService.currentTimeMillis();
    private String timestamp;
    private LogEntry.LogMessageType type;
    private HttpRequest[] httpRequests;
    private HttpResponse httpResponse;
    private HttpError httpError;
    private Expectation expectation;
    private Throwable throwable;
    private Runnable consumer;

    private String messageFormat;
    private Object[] arguments;
    private String message;

    public LogEntry() {

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
        return this;
    }

    public long getEpochTime() {
        return epochTime;
    }

    public LogEntry setEpochTime(long epochTime) {
        this.epochTime = epochTime;
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
        return httpRequests;
    }

    public LogEntry setHttpRequests(HttpRequest[] httpRequests) {
        this.httpRequests = httpRequests;
        return this;
    }

    public LogEntry setHttpRequest(HttpRequest httpRequest) {
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
        this.arguments = arguments;
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

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public LogEntry clone() {
        return new LogEntry()
            .setLogLevel(getLogLevel())
            .setEpochTime(getEpochTime())
            .setType(getType())
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
            .setLogLevel(getLogLevel())
            .setEpochTime(getEpochTime())
            .setType(getType())
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
        CREATED_EXPECTATION,
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
