package org.mockserver.serialization.model;

import org.mockserver.model.Body;
import org.mockserver.model.JsonPathBody;
import org.mockserver.model.NottableDateTimeString;

/**
 * @author jamesdbloom
 */
public class JsonPathBodyDTO extends BodyDTO {

    private final String jsonPath;
    private final NottableDateTimeString dateTime;

    public JsonPathBodyDTO(JsonPathBody jsonPathBody) {
        this(jsonPathBody, null);
    }

    public JsonPathBodyDTO(JsonPathBody jsonPathBody, Boolean not) {
        super(Body.Type.JSON_PATH, not);
        this.jsonPath = jsonPathBody.getValue();
        this.dateTime = jsonPathBody.getDateTime();
        withOptional(jsonPathBody.getOptional());
    }

    public String getJsonPath() {
        return jsonPath;
    }

    public NottableDateTimeString getDateTime() {
        return dateTime;
    }

    public JsonPathBody buildObject() {
        return (JsonPathBody) new JsonPathBody(getJsonPath(), getDateTime()).withOptional(getOptional());
    }
}
