package org.mockserver.serialization.model;

import org.mockserver.model.Body;
import org.mockserver.model.LogEntryBody;

/**
 * @author jamesdbloom
 */
public class LogEntryBodyDTO extends BodyWithContentTypeDTO {

    private final Object value;

    public LogEntryBodyDTO(LogEntryBody logEventBody) {
        super(Body.Type.STRING, null, null);
        value = logEventBody.getValue();
    }

    public Object getValue() {
        return value;
    }

    @Override
    public LogEntryBody buildObject() {
        return (LogEntryBody) new LogEntryBody(value).withOptional(getOptional());
    }
}
