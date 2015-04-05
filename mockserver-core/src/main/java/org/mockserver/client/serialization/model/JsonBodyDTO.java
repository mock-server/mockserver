package org.mockserver.client.serialization.model;

import org.mockserver.matchers.JsonBodyMatchType;
import org.mockserver.model.Body;
import org.mockserver.model.JsonBody;

/**
 * @author jamesdbloom
 */
public class JsonBodyDTO extends BodyDTO {

    private String json;
    private JsonBodyMatchType matchType;

    public JsonBodyDTO(JsonBody jsonBody) {
        this(jsonBody, false);
    }

    public JsonBodyDTO(JsonBody jsonBody, boolean not) {
        super(Body.Type.JSON, not);
        this.json = jsonBody.getValue();
        this.matchType = jsonBody.getMatchType();
    }

    protected JsonBodyDTO() {
    }

    public String getJson() {
        return json;
    }

    public JsonBodyMatchType getMatchType() {
        return matchType;
    }

    public JsonBody buildObject() {
        return new JsonBody(getJson(), matchType);
    }
}
