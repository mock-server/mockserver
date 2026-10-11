package org.mockserver.dashboard.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.dashboard.serializers.Description;
import org.mockserver.log.model.LogEntry;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.ObjectWithJsonToString;
import org.mockserver.model.RequestDefinition;

import java.util.Map;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public class DashboardLogEntryDTO extends ObjectWithJsonToString {

    private static final String[] EXCLUDED_FIELDS = {
        "id",
        "timestamp",
        "messageSizes",
        "argumentSizes",
        "argumentOwnMessageParts",
    };
    private String id;
    private String correlationId;
    private String timestamp;
    private LogEntry.LogMessageType type;
    private RequestDefinition[] httpRequests;
    private HttpResponse httpResponse;
    private Map<String, String> style;
    private String messageFormat;
    private Object[] arguments;
    private String[] throwable;
    private final DashboardBodyCap.Sizes messageSizes = new DashboardBodyCap.Sizes();
    private final DashboardBodyCap.Sizes argumentSizes = new DashboardBodyCap.Sizes();
    private String[] argumentOwnMessageParts;
    private String because;

    private Description description;

    public DashboardLogEntryDTO(String id, String correlationId, String timestamp, LogEntry.LogMessageType type) {
        setId(id);
        setCorrelationId(correlationId);
        setTimestamp(timestamp);
        setType(type);
    }

    public DashboardLogEntryDTO(LogEntry logEntry) {
        this(logEntry, null);
    }

    /**
     * @param configuration the effective server configuration, consulted for {@code redactSecretsInLog}
     *                      so secrets are masked in the dashboard when redaction is enabled on the
     *                      {@link org.mockserver.configuration.Configuration} instance (including via
     *                      {@code PUT /mockserver/configuration}); {@code null} falls back to the static store.
     */
    public DashboardLogEntryDTO(LogEntry logEntry, org.mockserver.configuration.Configuration configuration) {
        setId(logEntry.id());
        setCorrelationId(logEntry.getCorrelationId());
        setTimestamp(logEntry.getTimestamp());
        setType(logEntry.getType());
        // Bodies are cut to DashboardBodyCap.MAX_BODY_CHARACTERS after redaction, so a cut never exposes what
        // redaction would have hidden.
        DashboardBodyCap.Rendering rendering = new DashboardBodyCap.Rendering();
        LogEntry.RedactedView redacted = logEntry.redactedView(configuration, rendering);
        RequestDefinition[] requests = redacted.getHttpUpdatedRequests();
        for (int i = 0; i < requests.length; i++) {
            requests[i] = DashboardBodyCap.cap(requests[i], messageSizes, rendering);
        }
        setHttpRequests(requests);
        setHttpResponse(DashboardBodyCap.cap(redacted.getHttpUpdatedResponse(), messageSizes, rendering));
        setMessageFormat(redacted.getMessageFormat());
        Object[] arguments = redacted.getArguments();
        if (arguments != null) {
            argumentOwnMessageParts = new String[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = DashboardBodyCap.capArgument(arguments[i], argumentSizes, rendering);
                argumentOwnMessageParts[i] = logEntry.argumentOwnMessagePart(i);
            }
        }
        setArguments(arguments);
        Throwable throwable = redacted.getThrowable();
        if (throwable != null) {
            setThrowable(getStackTrace(throwable).split(System.lineSeparator()));
        }
        setBecause(DashboardBodyCap.capText(redacted.getBecause()));
    }

    /**
     * The full body length of a request or response of this entry (its own, or one of its message arguments)
     * whose body the dashboard cut short, or {@code null} when it was sent whole.
     */
    @JsonIgnore
    public Long originalBodyLength(Object message) {
        Long length = messageSizes.originalLength(message);
        return length != null ? length : argumentSizes.originalLength(message);
    }

    /**
     * Whether the argument at {@code index} is this entry's own request or response, so its full body can be
     * loaded from the entry; a request or response argument that is a different object cannot.
     */
    @JsonIgnore
    public boolean argumentLoadableFromEntry(int index) {
        return argumentOwnMessageParts != null && index >= 0 && index < argumentOwnMessageParts.length
            && argumentOwnMessageParts[index] != null;
    }

    /**
     * How many characters of a request's or response's body this entry sends (after any cut), or {@code null}
     * for a message that is not one of this entry's.
     */
    @JsonIgnore
    public Long shownBodyLength(Object message) {
        Long length = messageSizes.shownLength(message);
        return length != null ? length : argumentSizes.shownLength(message);
    }

    /**
     * Roughly how many characters this entry adds to an update as a log message (its arguments), used to
     * keep an update under its size ceiling.
     */
    @JsonIgnore
    public long estimatedLogMessageCharacters() {
        return DashboardBodyCap.MESSAGE_OVERHEAD_CHARACTERS
            + (messageFormat != null ? messageFormat.length() : 0)
            + argumentSizes.displayedCharacters()
            + (throwable != null ? throwable.length * 100L : 0);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
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

    public String[] getThrowable() {
        return throwable;
    }

    public void setThrowable(String[] throwable) {
        this.throwable = throwable;
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
