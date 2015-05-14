package org.mockserver.client.serialization.model;

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
    private Charset charset;

    public JsonBodyDTO(JsonBody jsonBody) {
        this(jsonBody, false);
    }

    public JsonBodyDTO(JsonBody jsonBody, Boolean not) {
        super(Body.Type.JSON, not);
        this.json = jsonBody.getValue();
        this.matchType = jsonBody.getMatchType();
        this.charset = jsonBody.getCharset();
    }

    protected JsonBodyDTO() {
    }

    public String getJson() {
        return json;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public Charset getCharset() {
        return charset;
    }

    public JsonBody buildObject() {
        return new JsonBody(getJson(), charset, matchType);
    }
}
