package org.mockserver.client.serialization.model;

import com.google.common.net.MediaType;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.Body;
import org.mockserver.model.JsonBody;

import java.nio.charset.Charset;

/**
 * @author jamesdbloom
 */
public class JsonBodyDTO extends BodyDTO {

    private String json;
    private MatchType matchType;

    public JsonBodyDTO(JsonBody jsonBody) {
        this(jsonBody, false);
    }

    public JsonBodyDTO(JsonBody jsonBody, Boolean not) {
        super(Body.Type.JSON, not, jsonBody.getContentType());
        this.json = jsonBody.getValue();
        this.matchType = jsonBody.getMatchType();
    }

    protected JsonBodyDTO() {
    }

    public String getJson() {
        return json;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public JsonBody buildObject() {
        return new JsonBody(getJson(), (contentType != null ? MediaType.parse(contentType) : null), matchType);
    }
}
