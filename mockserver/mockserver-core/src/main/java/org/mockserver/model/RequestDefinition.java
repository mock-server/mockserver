package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public abstract class RequestDefinition extends Not {

    private String logCorrelationId;

    @JsonIgnore
    public String getLogCorrelationId() {
        return logCorrelationId;
    }

    public RequestDefinition withLogCorrelationId(String logCorrelationId) {
        this.logCorrelationId = logCorrelationId;
        return this;
    }

    public abstract RequestDefinition shallowClone();

    public RequestDefinition cloneWithLogCorrelationId() {
        return MockServerLogger.isEnabled(Level.TRACE) && isNotBlank(getLogCorrelationId()) ? shallowClone().withLogCorrelationId(getLogCorrelationId()) : this;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
