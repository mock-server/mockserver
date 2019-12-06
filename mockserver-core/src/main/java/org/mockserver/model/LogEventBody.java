package org.mockserver.model;

/**
 * @author jamesdbloom
 */
public class LogEventBody extends BodyWithContentType<Object> {

    private final Object value;

    public LogEventBody(Object value) {
        super(Type.LOG_EVENT, null);
        this.value = value;
    }

    @Override
    public Object getValue() {
        return value;
    }
}
