package org.mockserver.serialization.model;

import org.mockserver.model.Body;
import org.mockserver.model.LogEventBody;

/**
 * @author jamesdbloom
 */
public class LogEventBodyDTO extends BodyWithContentTypeDTO {

    private Object value;

    public LogEventBodyDTO(LogEventBody logEventBody) {
        super(Body.Type.STRING, false, null);
        value = logEventBody.getValue();
    }

    public Object getValue() {
        return value;
    }

    @Override
    public LogEventBody buildObject() {
        return new LogEventBody(value);
    }
}
