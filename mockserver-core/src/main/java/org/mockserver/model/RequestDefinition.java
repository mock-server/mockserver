package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class RequestDefinition extends Not {

    private String logCorrelationId;

    @JsonIgnore
    public String getLogCorrelationId() {
        return logCorrelationId;
    }

    public RequestDefinition withLogCorrelationId(String logCorrelationId) {
        this.logCorrelationId = logCorrelationId;
        return this;
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
