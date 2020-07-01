package org.mockserver.dashboard.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.dashboard.serializers.Description;
import org.mockserver.log.model.LogEntry;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.ObjectWithJsonToString;
import org.mockserver.model.RequestDefinition;

import java.util.Map;

import static org.mockserver.model.HttpRequest.request;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public class DashboardLogEntryDTO extends ObjectWithJsonToString {

    private static final String[] EXCLUDED_FIELDS = {
        "id",
        "timestamp",
    };
    private String id;
    private String timestamp;
    private LogEntry.LogMessageType type;
    private RequestDefinition[] httpRequests;
    private HttpResponse httpResponse;
    private Map<String, String> style;
    private String messageFormat;
    private Object[] arguments;
    private String because;

    private Description description;

    public DashboardLogEntryDTO(LogEntry logEntry) {
        setId(logEntry.id());
        setTimestamp(logEntry.getTimestamp());
        setType(logEntry.getType());
        setHttpRequests(logEntry.getHttpUpdatedRequests());
        setHttpResponse(logEntry.getHttpUpdatedResponse());
        setMessageFormat(logEntry.getMessageFormat());
        setArguments(logEntry.getArguments());
        setBecause(logEntry.getBecause());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public DashboardLogEntryDTO setType(LogEntry.LogMessageType type) {
        this.type = type;
        return this;
    }

    @JsonIgnore
    public RequestDefinition[] getHttpRequests() {
        return httpRequests;
    }

    public DashboardLogEntryDTO setHttpRequests(RequestDefinition[] httpRequests) {
        this.httpRequests = httpRequests;
        return this;
    }

    public DashboardLogEntryDTO setHttpRequest(HttpRequest httpRequest) {
        if (httpRequest != null) {
            this.httpRequests = new HttpRequest[]{httpRequest};
        } else {
            this.httpRequests = new HttpRequest[]{request()};
        }
        return this;
    }

    public RequestDefinition getHttpRequest() {
        if (httpRequests != null && httpRequests.length > 0) {
            return httpRequests[0];
        } else {
            return null;
        }
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    public DashboardLogEntryDTO setHttpResponse(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
        return this;
    }

    public Map<String, String> getStyle() {
        return style;
    }

    public DashboardLogEntryDTO setStyle(Map<String, String> style) {
        this.style = style;
        return this;
    }

    public String getMessageFormat() {
        return messageFormat;
    }

    public DashboardLogEntryDTO setMessageFormat(String messageFormat) {
        this.messageFormat = messageFormat;
        return this;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public DashboardLogEntryDTO setArguments(Object... arguments) {
        this.arguments = arguments;
        return this;
    }

    @JsonIgnore
    public Object getBecause() {
        return because;
    }

    public DashboardLogEntryDTO setBecause(String because) {
        this.because = because;
        return this;
    }

    public Description getDescription() {
        return description;
    }

    public DashboardLogEntryDTO setDescription(Description description) {
        this.description = description;
        return this;
    }

    @Override
    protected String[] fieldsExcludedFromEqualsAndHashCode() {
        return EXCLUDED_FIELDS;
    }
}
