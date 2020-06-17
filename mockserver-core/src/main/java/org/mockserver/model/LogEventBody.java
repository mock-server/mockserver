package org.mockserver.model;

import java.util.Objects;

/**
 * @author jamesdbloom
 */
public class LogEventBody extends BodyWithContentType<Object> {
    private int hashCode;
    private final Object value;

    public LogEventBody(Object value) {
        super(Type.LOG_EVENT, null);
        this.value = value;
    }

    @Override
    public Object getValue() {
        return value;
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
        if (!super.equals(o)) {
            return false;
        }
        LogEventBody that = (LogEventBody) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), value);
        }
        return hashCode;
    }
}
